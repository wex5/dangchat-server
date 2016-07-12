package com.justep.x5.sound.converter;

import it.sauronsoftware.jave.*;

import java.io.File;


public class AudioConverter {
	public void encodeWav(String sourcePath){
		String withoutExtensionFileName = sourcePath.substring(0, sourcePath.lastIndexOf("."));
		File sourceFile = new File(sourcePath);
		File aacTarget = new File(withoutExtensionFileName + ".aac");
		File oggTarget = new File(withoutExtensionFileName + ".ogg");
		encodeWavToAac(sourceFile, aacTarget);
		encodeWavToOgg(sourceFile, oggTarget);
	}

	public void encodeWavToAac(File source){
		String targetFileName = this.getTargetFileName(source, "aac");
		File targetFile = new File(targetFileName);
		this.encodeWavToAac(source, targetFile);
	}

	public void encodeWavToOgg(File source){
		String targetFileName = this.getTargetFileName(source, "ogg");
		File targetFile = new File(targetFileName);
		this.encodeWavToOgg(source, targetFile);
	}
	
	public void encodeWavToAac(File source, File target){
		AudioAttributes audio = new AudioAttributes();    
		audio.setCodec("libfaac");
		audio.setBitRate(new Integer(12800));  
		audio.setChannels(new Integer(1));
		audio.setSamplingRate(new Integer(44100));   
		    
		EncodingAttributes attrs = new EncodingAttributes();    
		attrs.setFormat("aac");    
		attrs.setAudioAttributes(audio);
		Encoder encoder = new Encoder();
		try {
			encoder.encode(source, target, attrs);
		} catch (IllegalArgumentException | EncoderException e) {
			e.printStackTrace();
		}
	}
	
	public void encodeWavToOgg(File source, File target){		
		AudioAttributes audio = new AudioAttributes();
		//windows下使用libvorbis，linux下使用vorbis
		if(this.osIsWindows()) {
			audio.setCodec("libvorbis");
		} else {
			audio.setCodec("vorbis");
		}
		audio.setBitRate(new Integer(12800));    
		audio.setChannels(new Integer(2));
		audio.setSamplingRate(new Integer(44100));   
		    
		EncodingAttributes attrs = new EncodingAttributes();
		attrs.setFormat("ogg");   
		attrs.setAudioAttributes(audio);
		Encoder encoder = new Encoder();
		try {
			encoder.encode(source, target, attrs);
		} catch (IllegalArgumentException | EncoderException e) {
			e.printStackTrace();
		}
	}

	public boolean isWavFile(File source) throws EncoderException {
		Encoder encoder = new Encoder();
		MultimediaInfo info = encoder.getInfo(source);
		return info.getFormat().toLowerCase().equals("wav");
	}

	private String getTargetFileName(File sourceFile, String fileExtension) {
		String fileName = sourceFile.getAbsolutePath();
		String withoutExtensionFileName = fileName.substring(0, fileName.lastIndexOf("."));
		return withoutExtensionFileName + "." + fileExtension;
	}
	
	/**
	 * 判断运行环境是否为Windows操作系统
	 * @return
	 */
	private boolean osIsWindows() {
		String osName = System.getProperty("os.name").toLowerCase();
		return osName.indexOf("win") >= 0
	}
	
}