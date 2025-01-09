package com.andcool;

import com.andcool.API.HTTPException;
import com.andcool.Encryption.Keys;
import com.andcool.SillyLogger.Level;
import com.andcool.SillyLogger.SillyLogger;
import com.andcool.session.SessionHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.json.JSONObject;

import javax.crypto.SecretKey;
import java.net.UnknownHostException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class MinecraftClient implements Callable<JSONObject> {
    private final String SERVER_ADDRESS;
    private final int SERVER_PORT;
    private final String PLAYER_NAME;
    private final String UUID;
    private final int PROTOCOL_VERSION;
    private final String accessToken;
    private final SecretKey sharedSecret = Keys.generateRandomSecret();
    public static final SillyLogger logger = new SillyLogger("", true, Level.DEBUG);

    public MinecraftClient(String address,
                           int port,
                           String nick,
                           String uuid,
                           int protoVer,
                           String token) {
        SERVER_ADDRESS = address;
        SERVER_PORT = port;
        PLAYER_NAME = nick;
        UUID = uuid;
        PROTOCOL_VERSION = protoVer;
        accessToken = token;
    }

    public JSONObject call() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();

        CompletableFuture<JSONObject> future = new CompletableFuture<JSONObject>();
        SessionHandler session = new SessionHandler(
                SERVER_ADDRESS,
                SERVER_PORT,
                PLAYER_NAME,
                UUID,
                PROTOCOL_VERSION,
                sharedSecret,
                accessToken,
                future
        );

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(session);
                        }
                    });

            Channel channel = bootstrap.connect(SERVER_ADDRESS, SERVER_PORT).sync().channel();
            channel.closeFuture().sync();
            return future.get();
        } catch (Exception e) {
            if (e instanceof ExecutionException executionException) {
                throw (Exception) executionException.getCause();
            }

            if (e instanceof UnknownHostException) {
                throw new HTTPException("Unknown host", 400);
            }
            throw e;
        } finally {
            logger.log(Level.INFO, "Shutting down minecraft client gracefully...");
            group.shutdownGracefully();
        }
    }
}
