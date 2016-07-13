package com.justep.dangchat.media.converter.video;

import it.sauronsoftware.jave.*;

import java.io.File;

/**
 * Created by Lining on 2016/7/13.
 */
public class VideoConverter {

    /**
     * 将视频文件转换为ogg格式
     * @param sourceFile	the source file
     */
    public void toOggVideo(File sourceFile) {
        String targetFileName = this.getTargetFileName(sourceFile, "ogg");
        File targetFile = new File(targetFileName);

        AudioAttributes audio = new AudioAttributes();
        if(osIsWindows()) {
            audio.setCodec("libvorbis");
        } else {
            audio.setCodec("vorbis");
        }
        audio.setBitRate(new Integer(12800));
        audio.setSamplingRate(new Integer(44100));
        audio.setChannels(new Integer(2));

        VideoAttributes video = new VideoAttributes();
        video.setCodec("libtheora");
        video.setBitRate(new Integer(160000));
        video.setFrameRate(new Integer(15));
        video.setSize(new VideoSize(512, 384));

        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setFormat("ogg");
        attrs.setAudioAttributes(audio);
        attrs.setVideoAttributes(video);
        Encoder encoder = new Encoder();
        try {
            encoder.encode(sourceFile, targetFile, attrs);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 得到目标文件名称
     * @param sourceFile	the source file
     * @param fileExtension		the extension name of file	
     * @return
     */
    private String getTargetFileName(File sourceFile, String fileExtension) {
        String fileName = sourceFile.getAbsolutePath();
        String withoutExtensionFileName = fileName.substring(0, fileName.lastIndexOf("."));
        return withoutExtensionFileName + "." + fileExtension;
    }

    /**
     * 判断当前运行环境是否为Windows
     * @return
     */
    private boolean osIsWindows() {
        String osName = System.getProperty("os.name").toLowerCase();
        return osName.indexOf("win") >= 0;
    }

}
