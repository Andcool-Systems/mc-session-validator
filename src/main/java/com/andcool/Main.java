package com.andcool;

import com.andcool.Encryption.Keys;
import com.andcool.session.SessionHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import javax.crypto.SecretKey;

public class Main {

    // Конфигурация
    public static final String SERVER_ADDRESS = "play.pepeland.net";
    public static final int SERVER_PORT = 25565;
    public static final String PLAYER_NAME = "Vanessa_52";
    public static final String UUID = "33256ed7-e564-417f-9da3-cacada70a590";
    public static final int PROTOCOL_VERSION = 768;
    public static final SecretKey sharedSecret = Keys.generateRandomSecret();

    public static void main(String[] args) throws InterruptedException {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new SessionHandler());
                        }
                    });

            // Подключаемся к серверу
            Channel channel = bootstrap.connect(SERVER_ADDRESS, SERVER_PORT).sync().channel();
            channel.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
