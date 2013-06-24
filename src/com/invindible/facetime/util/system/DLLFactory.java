package com.invindible.facetime.util.system;

import java.io.File;
import java.net.URI;

public class DLLFactory {

	/**
	 * 加载本地dll文件
	 * @param path	当前dll文件的相对路径
	 */
	public static void loadDLL(Class<?> cls, String path) {
		try {
			URI uri = cls.getClass().getResource(path).toURI();
			String realPath = new File(uri).getAbsolutePath();
			System.load(realPath);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 加载本地的jmf依赖的dll文件
	 * @param cls
	 */
	public static void loadJmfDLL(Class<?> cls) {
		loadDLL(cls, "/dll/jmf/jmfjawt.dll");
		loadDLL(cls, "/dll/jmf/jmmci.dll");
		loadDLL(cls, "/dll/jmf/jmutil.dll");
		loadDLL(cls, "/dll/jmf/jmjpeg.dll");
		loadDLL(cls, "/dll/jmf/jmmpegv.dll");
		loadDLL(cls, "/dll/jmf/jmacm.dll");
		loadDLL(cls, "/dll/jmf/jmam.dll");
		loadDLL(cls, "/dll/jmf/jmvh263.dll");
		loadDLL(cls, "/dll/jmf/jmh263enc.dll");
		loadDLL(cls, "/dll/jmf/jmgsm.dll");
		loadDLL(cls, "/dll/jmf/jmvcm.dll");
		loadDLL(cls, "/dll/jmf/jmmpa.dll");
		loadDLL(cls, "/dll/jmf/jmdaudc.dll");
		loadDLL(cls, "/dll/jmf/jmdaud.dll");
		loadDLL(cls, "/dll/jmf/jmgdi.dll");
		loadDLL(cls, "/dll/jmf/jmcvid.dll");
		loadDLL(cls, "/dll/jmf/jmddraw.dll");
		loadDLL(cls, "/dll/jmf/jmg723.dll");
		loadDLL(cls, "/dll/jmf/jmh261.dll");
		loadDLL(cls, "/dll/jmf/jmvfw.dll");
//		try {
//			URI uri = cls.getClass().getResource("/dll/jmf").toURI();
//			File jmfPath = new File(uri);
//			File[] jmfDlls = jmfPath.listFiles();
//			for(File f : jmfDlls) {
//				if(f.isFile()) {
//					System.load(f.getAbsolutePath());
//				}
//			}
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
	}
	
}
