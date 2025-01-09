package com.andcool.session;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.andcool.API.HTTPException;
import com.andcool.Compression.PacketDeflater;
import com.andcool.Compression.PacketInflater;
import com.andcool.Encryption.Encryption;

import com.andcool.Bytebuf.ByteBufUtils;

import com.andcool.Mojang.Join;
import com.andcool.SillyLogger.Level;
import com.andcool.SillyLogger.SillyLogger;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.json.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

public class SessionHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private final String SERVER_ADDRESS;
    private final int SERVER_PORT;
    private final String PLAYER_NAME;
    private final String PLAYER_UUID;
    private final int PROTOCOL_VERSION;
    private final SecretKey sharedSecret;
    private final String accessToken;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public static final SillyLogger logger = new SillyLogger("", true, Level.DEBUG);
    private final CompletableFuture<JSONObject> future;

    public SessionHandler(String address,
                          int port,
                          String nick,
                          String uuid,
                          int protoVer,
                          SecretKey secret,
                          String token,
                          CompletableFuture<JSONObject> future)
    {
        SERVER_ADDRESS = address;
        SERVER_PORT = port;
        PLAYER_NAME = nick;
        PLAYER_UUID = uuid;
        PROTOCOL_VERSION = protoVer;
        sharedSecret = secret;
        accessToken = token;
        this.future = future;
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws IOException {
        logger.log(Level.INFO, "Connected to a " + SERVER_ADDRESS + ". Starting handshaking...");

        scheduler.schedule(() -> {
            ctx.close();
        }, 15, TimeUnit.SECONDS);

        ByteBuf out = ctx.alloc().buffer();
        ByteBufUtils.writeVarInt(out, 0x00); // Packet ID
        ByteBufUtils.writeVarInt(out, PROTOCOL_VERSION);
        ByteBufUtils.writeUTF8(out, SERVER_ADDRESS);
        out.writeShort(SERVER_PORT);
        ByteBufUtils.writeVarInt(out, 2);

        ctx.write(ByteBufUtils.addSize(ctx, out));

        ByteBuf outLogin = ctx.alloc().buffer();
        ByteBufUtils.writeVarInt(outLogin, 0x00); // Packet ID
        ByteBufUtils.writeUTF8(outLogin, PLAYER_NAME);
        ByteBufUtils.writeUUID(outLogin, UUID.fromString(PLAYER_UUID));

        ctx.write(ByteBufUtils.addSize(ctx, outLogin));
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        int packetLength = ByteBufUtils.readVarInt(in);
        int packetId = ByteBufUtils.readVarInt(in);

        logger.log(Level.DEBUG, "Packet received! Packet id: " + packetId + " Packet length: " + packetLength);

        if (packetId == 0x00) {
            if (ctx.pipeline().get("decompress") instanceof PacketInflater) {
                ByteBufUtils.readVarInt(in);
            }
            String json = ByteBufUtils.readUTF8(in);

            JSONObject errorResponse = new JSONObject();
            errorResponse.put("message", new JSONObject(json));

            logger.log(Level.ERROR, "Disconnected from server with message: " + json);

            throw new HTTPException(errorResponse, 403);

        } else if (packetId == 0x01) {
            logger.log(Level.DEBUG, "Received encryption request");

            String serverId = ByteBufUtils.readUTF8(in);

            int publicKeyLength = ByteBufUtils.readVarInt(in);
            byte[] publicKeyBytes = new byte[publicKeyLength];
            in.readBytes(publicKeyBytes);

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

            int verifyTokenLength = ByteBufUtils.readVarInt(in);
            byte[] verifyToken = new byte[verifyTokenLength];
            in.readBytes(verifyToken);

            in.readBoolean();

            Join.join(serverId, publicKey, PLAYER_UUID, sharedSecret, accessToken);

            logger.log(Level.DEBUG, "Sending encryption response packet (id: 0x01)");

            Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);

            byte[] encryptedSharedSecret = rsaCipher.doFinal(sharedSecret.getEncoded());
            byte[] encryptedVerifyToken = rsaCipher.doFinal(verifyToken);

            ByteBuf out = ctx.alloc().buffer();
            ByteBufUtils.writeVarInt(out, 0x01); // Packet ID
            ByteBufUtils.writeVarInt(out, encryptedSharedSecret.length);
            out.writeBytes(encryptedSharedSecret);
            ByteBufUtils.writeVarInt(out, encryptedVerifyToken.length);
            out.writeBytes(encryptedVerifyToken);

            ctx.write(ByteBufUtils.addSize(ctx, out));

            ctx.pipeline().addFirst("decoder", new Encryption(sharedSecret));
            ctx.flush();
        } else if (packetId == 2) {
            UUID uuid = ByteBufUtils.readUUID(in);
            String nick = ByteBufUtils.readUTF8(in);

            int userdataLength = ByteBufUtils.readVarInt(in);
            byte[] userDataBytes = new byte[userdataLength];
            in.readBytes(userDataBytes);

            JSONObject response = new JSONObject();
            response.put("nickname", nick);
            response.put("UUID", uuid);

            future.complete(response);
            ctx.close();
        } else if (packetId == 3) {
            int compressionThreshold = ByteBufUtils.readVarInt(in);
            logger.log(Level.INFO, "Server requested compression with threshold: " + compressionThreshold);
            enableCompression(ctx, compressionThreshold);
        } else {
            logger.log(Level.WARN, "Invalid packet ID: " + packetId);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.log(Level.INFO, "Session closed!");
        ctx.fireChannelInactive();
        scheduler.shutdown();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        future.completeExceptionally(cause);
    }

    private void enableCompression(ChannelHandlerContext ctx, int compressionThreshold) {
        if (compressionThreshold >= 0) {
            if (ctx.pipeline().get("decompress") instanceof PacketInflater) {
                ((PacketInflater)ctx.pipeline().get("decompress")).setCompressionThreshold(compressionThreshold, false);
            } else {
                PacketInflater inf = new PacketInflater(compressionThreshold, false);
                ctx.pipeline().addAfter("decoder", "decompress", inf);
                logger.log(Level.INFO, "Decompressor pipeline set to threshold " + compressionThreshold);
            }
        } else {
            if (ctx.pipeline().get("decompress") instanceof PacketInflater) {
                ctx.pipeline().remove("decompress");
            }
            if (ctx.pipeline().get("compress") instanceof PacketDeflater) {
                ctx.pipeline().remove("compress");
            }
        }
    }
}