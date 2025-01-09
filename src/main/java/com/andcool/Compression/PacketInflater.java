package com.andcool.Compression;

import com.andcool.Bytebuf.ByteBufUtils;
import com.andcool.Main;
import com.andcool.SillyLogger.Level;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;

import java.util.Arrays;
import java.util.List;
import java.util.zip.Inflater;

public class PacketInflater extends ByteToMessageDecoder {
    /**
     * The maximum size allowed for a compressed packet. Has value {@value}.
     */
    public static final int MAXIMUM_PACKET_SIZE = 0x800000;
    private final Inflater inflater;
    private int compressionThreshold;
    private boolean rejectsBadPackets;

    public PacketInflater(int compressionThreshold, boolean rejectsBadPackets) {
        this.compressionThreshold = compressionThreshold;
        this.rejectsBadPackets = rejectsBadPackets;
        this.inflater = new Inflater();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buf, List<Object> objects) throws Exception {
        Main.logger.log(Level.DEBUG, "Inflater got a packet!");

        if (buf.readableBytes() == 0) {
            return;
        }

        int packetLength = ByteBufUtils.readVarInt(buf);
        int uncompressedLength = ByteBufUtils.readVarInt(buf);

        if (uncompressedLength == 0) {
            buf.resetReaderIndex();
            objects.add(buf.readBytes(buf.readableBytes()));
            return;
        }

        byte[] compressedData = new byte[buf.readableBytes()];
        buf.readBytes(compressedData);
        inflater.setInput(compressedData);

        byte[] decompressedData = new byte[uncompressedLength];
        inflater.inflate(decompressedData);
        inflater.reset();

        ByteBuf uncompressedBuf = Unpooled.buffer();
        ByteBufUtils.writeVarInt(uncompressedBuf, decompressedData.length);
        uncompressedBuf.writeBytes(decompressedData);

        objects.add(uncompressedBuf);
    }



    public void setCompressionThreshold(int compressionThreshold, boolean rejectsBadPackets) {
        this.compressionThreshold = compressionThreshold;
        this.rejectsBadPackets = rejectsBadPackets;
    }
}

