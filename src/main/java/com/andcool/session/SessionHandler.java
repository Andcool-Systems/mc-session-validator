package com.andcool.session;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;

import com.andcool.Compression.PacketDeflater;
import com.andcool.Compression.PacketInflater;
import com.andcool.Encryption.Encryption;
import com.andcool.Main;

import com.andcool.Bytebuf.ByteBufUtils;

import com.andcool.Mojang.Join;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import javax.crypto.Cipher;

public class SessionHandler extends SimpleChannelInboundHandler<ByteBuf> {

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws IOException {
        Session session = new Session();
        SessionUtil.setSession(ctx.channel(), session);

        System.out.println("Connected to a server. Starting handshaking...");

        ByteBuf out = ctx.alloc().buffer();
        ByteBufUtils.writeVarInt(out, 0x00); // Packet ID
        ByteBufUtils.writeVarInt(out, Main.PROTOCOL_VERSION);
        ByteBufUtils.writeUTF8(out, Main.SERVER_ADDRESS);
        out.writeShort(Main.SERVER_PORT);
        ByteBufUtils.writeVarInt(out, 2);

        ctx.write(ByteBufUtils.addSize(ctx, out));

        ByteBuf outLogin = ctx.alloc().buffer();
        ByteBufUtils.writeVarInt(outLogin, 0x00); // Packet ID
        ByteBufUtils.writeUTF8(outLogin, Main.PLAYER_NAME);
        ByteBufUtils.writeUUID(outLogin, UUID.fromString(Main.UUID));

        ctx.write(ByteBufUtils.addSize(ctx, outLogin));
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf in) {
        try {
            /*
            in.markReaderIndex();

            System.out.print("Raw ByteBuf: ");
            while (in.isReadable()) {
                System.out.printf("%02X ", in.readUnsignedByte());
            }
            System.out.println();

            in.resetReaderIndex();
            */

            int packetLength = ByteBufUtils.readVarInt(in);
            int packetId = ByteBufUtils.readVarInt(in);

            System.out.println("Packet got! Length: " + packetLength + " Id: " + packetId);

            //String str = in.toString(in.readerIndex(), in.readableBytes(), java.nio.charset.StandardCharsets.UTF_8);
            //System.out.println("ByteBuf contents as UTF-8 string: " + str);

            if (packetId == 0x00) {
                if (ctx.pipeline().get("decompress") instanceof PacketInflater) {
                    ByteBufUtils.readVarInt(in);
                }
                String json = ByteBufUtils.readUTF8(in);
                System.out.println("JSON: " + json);

            } else if (packetId == 0x01) {
                System.out.println("Received encryption request");

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

                Join.join(serverId, publicKey);

                Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);

                byte[] encryptedSharedSecret = rsaCipher.doFinal(Main.sharedSecret.getEncoded());
                byte[] encryptedVerifyToken = rsaCipher.doFinal(verifyToken);

                ByteBuf out = ctx.alloc().buffer();
                ByteBufUtils.writeVarInt(out, 0x01); // Packet ID
                ByteBufUtils.writeVarInt(out, encryptedSharedSecret.length);
                out.writeBytes(encryptedSharedSecret);
                ByteBufUtils.writeVarInt(out, encryptedVerifyToken.length);
                out.writeBytes(encryptedVerifyToken);

                ctx.write(ByteBufUtils.addSize(ctx, out));

                ctx.pipeline().addFirst("decoder", new Encryption(Main.sharedSecret));
                ctx.flush();
            } else if (packetId == 2) {
                UUID uuid = ByteBufUtils.readUUID(in);
                String nick = ByteBufUtils.readUTF8(in);

                int userdataLength = ByteBufUtils.readVarInt(in);
                byte[] userDataBytes = new byte[userdataLength];
                in.readBytes(userDataBytes);

                System.out.println(uuid + " " + nick);
                ctx.close();
            } else if (packetId == 3) {
                int compressionThreshold = ByteBufUtils.readVarInt(in);
                System.out.println("Compression threshold: " + compressionThreshold);
                enableCompression(ctx, compressionThreshold);
            } else {
                System.out.println("Invalid packet ID: " + packetId);
            }
        } catch (Exception e) {
            e.getStackTrace();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        System.out.println("Session closed!");
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws IOException {
        cause.printStackTrace();
    }

    private void enableCompression(ChannelHandlerContext ctx, int compressionThreshold) {
        if (compressionThreshold >= 0) {
            if (ctx.pipeline().get("decompress") instanceof PacketInflater) {
                ((PacketInflater)ctx.pipeline().get("decompress")).setCompressionThreshold(compressionThreshold, false);
            } else {
                PacketInflater inf = new PacketInflater(compressionThreshold, false);
                ctx.pipeline().addAfter("decoder", "decompress", inf);
                System.out.println("Decompressor pipeline set to threshold " + compressionThreshold);
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