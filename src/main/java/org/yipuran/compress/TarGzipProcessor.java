package org.yipuran.compress;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.yipuran.file.FileCollection;

/**
 * tar and gzip 圧縮／解凍プロセッサ.
 * <PRE>
 * （規則）
 *     tar          ファイル名拡張子 → .tar
 *     gzip         ファイル名拡張子 → .gz
 *     tar and gzip ファイル名拡張子 → .tar.gz
 * （圧縮）
 * Supplier<Collection<FileCollection>> = ファイルコレクション（FileCollection）で指定する対象を
 * Collection<String> compress(String targzPath) で圧縮する。
 * メソッド戻り値は、tarエントリ名 Collection
 * （展開）
 * void decompress(String targzPath, String dirPath) で展開する。
 * </PRE>
 */
public interface TarGzipProcessor extends Supplier<Collection<FileCollection>>{

	/**
	 * tar and gzip 圧縮実行.
	 * <PRE>
	 * Supplier で渡す FileCollection の渡し方で単一か複数か決まる。
	 * 例１）
	 *    // targetPath配下を圧縮対象にする場合
	 *    List<FileCollection> fileCollections =
	 *    Arrays.stream(new File(targetPath).listFiles()).map(e->FileCollection.of(e.getAbsolutePath())).collect(Collectors.toList());
	 *    TarGzipProcessor processor = ()->fileCollections;
	 *    Collection<String> entries = processor.compress(targzipPath);
	 *
	 * 例２）
	 *    // １つのディレクトリツリーで圧縮
	 *    FileCollection fileCollection = FileCollection.of(targetPath);
	 *    TarGzipProcessor processor = ()->Arrays.asList(fileCollection);
	 *    Collection<String> entries = processor.compress(targzipPath);
	 *
	 * </PRE>
	 * @param targzPath 作成する tar and gzip ファイルパス、 *.tar.gz
	 * @return tarエントリ名 Collection
	 */
	public default Collection<String> compress(String targzPath){
		Collection<String> entries = new ArrayList<>();
		String tarpath = targzPath.replaceAll("\\.tar\\.gz$", ".tar");
		// tar 生成
		try(FileOutputStream out = new FileOutputStream(tarpath);TarArchiveOutputStream taos = new TarArchiveOutputStream(out)){
			get().forEach(fc->{
				String prefix = fc.getFile().getParentFile().getAbsolutePath().replaceAll("\\\\", "/");
				fc.scan(f->{
					try{
						if (f.isDirectory()){
							TarArchiveEntry entry = new TarArchiveEntry(fc.getFile(), f.getAbsolutePath().replaceAll("\\\\", "/").replaceFirst(prefix, ""));
							taos.putArchiveEntry(entry);
							taos.closeArchiveEntry();
							entries.add(entry.getName());
							return;
						}
						TarArchiveEntry entry = new TarArchiveEntry(f, f.getAbsolutePath().replaceAll("\\\\", "/").replaceFirst(prefix, ""));
						taos.putArchiveEntry(entry);
						//taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
						taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
						try(FileInputStream fis = new FileInputStream(f); BufferedInputStream bis = new BufferedInputStream(fis)){
							int size = 0;
							byte[] buf = new byte[1024];
							while((size = bis.read(buf)) > 0){
								taos.write(buf, 0, size);
							}
						}
						taos.closeArchiveEntry();
						entries.add(entry.getName());
					}catch(IOException ex){
						throw new RuntimeException(ex);
					}
				});
			});
		}catch(IOException ex){
			throw new RuntimeException(ex.getMessage(), ex);
		}
		// gzip 生成
		try(FileInputStream fis = new FileInputStream(tarpath); BufferedInputStream bis = new BufferedInputStream(fis);
			FileOutputStream fos = new FileOutputStream(targzPath); GzipCompressorOutputStream gout = new GzipCompressorOutputStream(fos)
		){
			int size = 0;
			byte[] buf = new byte[1024];
			while((size = bis.read(buf)) > 0){
				gout.write(buf, 0, size);
			}
		}catch(IOException ex){
			throw new RuntimeException(ex.getMessage(), ex);
		}
		new File(tarpath).delete();
		return entries;
	}

	/**
	 * tar and gzip 圧縮実行（対象制限）.
	 * <PRE>
	 * Predicate<File> で、tar作成対象を制限する。任意ディレクトリパスなど制限するために使用する。
	 * </PRE>
	 * @param targzPath 作成する tar and gzip ファイルパス、 *.tar.gz
	 * @param p Predicate<File>制限規則の付与
	 * @return tarエントリ名 Collection
	 */
	public default Collection<String> compress(String targzPath, Predicate<File> p){
		Collection<String> entries = new ArrayList<>();
		String tarpath = targzPath.replaceAll("\\.tar\\.gz$", ".tar");
		// tar 生成
		try(FileOutputStream out = new FileOutputStream(tarpath);TarArchiveOutputStream taos = new TarArchiveOutputStream(out)){
			get().forEach(fc->{
				String prefix = fc.getFile().getParentFile().getAbsolutePath().replaceAll("\\\\", "/");
				fc.scan(p, f->{
					try{
						if (f.isDirectory()){
							TarArchiveEntry entry = new TarArchiveEntry(fc.getFile(), f.getAbsolutePath().replaceAll("\\\\", "/").replaceFirst(prefix, ""));
							taos.putArchiveEntry(entry);
							taos.closeArchiveEntry();
							entries.add(entry.getName());
							return;
						}
						TarArchiveEntry entry = new TarArchiveEntry(f, f.getAbsolutePath().replaceAll("\\\\", "/").replaceFirst(prefix, ""));
						taos.putArchiveEntry(entry);
						//taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
						taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
						try(FileInputStream fis = new FileInputStream(f); BufferedInputStream bis = new BufferedInputStream(fis)){
							int size = 0;
							byte[] buf = new byte[1024];
							while((size = bis.read(buf)) > 0){
								taos.write(buf, 0, size);
							}
						}
						taos.closeArchiveEntry();
						entries.add(entry.getName());
					}catch(IOException ex){
						throw new RuntimeException(ex);
					}
				});
			});
		}catch(IOException ex){
			throw new RuntimeException(ex.getMessage(), ex);
		}
		// gzip 生成
		try(FileInputStream fis = new FileInputStream(tarpath); BufferedInputStream bis = new BufferedInputStream(fis);
			FileOutputStream fos = new FileOutputStream(targzPath); GzipCompressorOutputStream gout = new GzipCompressorOutputStream(fos)
		){
			int size = 0;
			byte[] buf = new byte[1024];
			while((size = bis.read(buf)) > 0){
				gout.write(buf, 0, size);
			}
		}catch(IOException ex){
			throw new RuntimeException(ex.getMessage(), ex);
		}
		new File(tarpath).delete();
		return entries;
	}


	/**
	 * tar and gzip 展開.
	 * @param targzPath tar and gzip ファイルパス、 *.tar.gz
	 * @param dirPath 展開先ディレクトリPATH
	 * @return Collection<String> 展開された tar エントリ名
	 */
	public static Collection<String> decompress(String targzPath, String dirPath){
		Collection<String> entries = new ArrayList<>();
		String tarname = targzPath.substring(targzPath.lastIndexOf("/") + 1).replaceAll("\\.gz$", "");
		// gzip 解凍
		try(FileInputStream fis = new FileInputStream(targzPath);GzipCompressorInputStream gin = new GzipCompressorInputStream(fis);
			FileOutputStream  fos = new FileOutputStream(dirPath + "/" + tarname)
		){
			int size = 0;
			byte[] buf = new byte[1024];
			while((size = gin.read(buf)) > 0){
				fos.write(buf, 0, size);
			}
		}catch(IOException ex){
			throw new RuntimeException(ex.getMessage(), ex);
		}
		// tar展開
		try(FileInputStream fis = new FileInputStream(dirPath + "/" + tarname);	TarArchiveInputStream tais = new TarArchiveInputStream(fis)){
			ArchiveEntry entry = null;
			while((entry = tais.getNextEntry()) != null){
				File file = new File(dirPath + "/" + entry.getName());
				if (entry.isDirectory()){
					file.mkdirs();
					entries.add(entry.getName());
					continue;
				}
				if (!file.getParentFile().exists()){ file.getParentFile().mkdirs(); }
				try(FileOutputStream fos = new FileOutputStream(file);
						BufferedOutputStream bos = new BufferedOutputStream(fos)){
					int size = 0;
					byte[] buf = new byte[1024];
					while((size = tais.read(buf)) > 0){
						bos.write(buf, 0, size);
					}
					entries.add(entry.getName());
				}
			}
		}catch(IOException ex){
			throw new RuntimeException(ex.getMessage(), ex);
		}
		new File(dirPath + "/" + tarname).delete();
		return entries;
	}

	/**
	 * tar and gzip エントリ名コレクション.
	 * @param targzPath tar and gzip ファイルパス、 *.tar.gz
	 * @return Collection<String>
	 */
	public static Collection<String> viewPath(String targzPath){
		Collection<String> entries = new ArrayList<>();
		try(	FileInputStream fis = new FileInputStream(targzPath);
				GzipCompressorInputStream gin = new GzipCompressorInputStream(fis);
				PipedOutputStream pos = new PipedOutputStream();
				PipedInputStream pin = new PipedInputStream();
				TarArchiveInputStream tais = new TarArchiveInputStream(pin)
		){
			pin.connect(pos);
			new Thread(()->{
				try{
					int size = 0;
					byte[] buf = new byte[1024];
					while((size = gin.read(buf)) >= 0){
						pos.write(buf, 0, size);
						pos.flush();
					}
				}catch(IOException e){
					throw new RuntimeException(e.getMessage(), e);
				}
			}).start();
			ArchiveEntry entry = null;
			while((entry = tais.getNextEntry()) != null){
				entries.add(entry.getName());
			}
		}catch(IOException ex){
			throw new RuntimeException(ex.getMessage(), ex);
		}
		return entries;
	}
	/**
	 * ArchiveEntryコレクション.
	 * @param targzPath tar and gzip ファイルパス、 *.tar.gz
	 * @return Collection<ArchiveEntry>
	 */
	public static Collection<ArchiveEntry> entries(String targzPath){
		Collection<ArchiveEntry> entries = new ArrayList<>();
		try(	FileInputStream fis = new FileInputStream(targzPath);
				GzipCompressorInputStream gin = new GzipCompressorInputStream(fis);
				PipedOutputStream pos = new PipedOutputStream();
				PipedInputStream pin = new PipedInputStream();
				TarArchiveInputStream tais = new TarArchiveInputStream(pin)
		){
			pin.connect(pos);
			new Thread(()->{
				try{
					int size = 0;
					byte[] buf = new byte[1024];
					while((size = gin.read(buf)) >= 0){
						pos.write(buf, 0, size);
						pos.flush();
					}
				}catch(IOException e){
					throw new RuntimeException(e.getMessage(), e);
				}
			}).start();
			ArchiveEntry entry = null;
			while((entry = tais.getNextEntry()) != null){
				entries.add(entry);
			}
		}catch(IOException ex){
			throw new RuntimeException(ex.getMessage(), ex);
		}
		return entries;
	}
	/**
	 * Predicate→ArchiveEntryコレクション.
	 * @param targzPath tar and gzip ファイルパス、 *.tar.gz
	 * @param p Predicate<ArchiveEntry> ファイルのArchiveEntry の Predicate
	 * @return Collection<ArchiveEntry>
	 */
	public static Collection<ArchiveEntry> entries(String targzPath, Predicate<ArchiveEntry> p){
		Collection<ArchiveEntry> entries = new ArrayList<>();
		try(	FileInputStream fis = new FileInputStream(targzPath);
				GzipCompressorInputStream gin = new GzipCompressorInputStream(fis);
				PipedOutputStream pos = new PipedOutputStream();
				PipedInputStream pin = new PipedInputStream();
				TarArchiveInputStream tais = new TarArchiveInputStream(pin)
		){
			pin.connect(pos);
			try{
				Thread th = new Thread(()->{
					try{
						int size = 0;
						byte[] buf = new byte[1024];
						while((size = gin.read(buf)) >= 0){
							pos.write(buf, 0, size);
							pos.flush();
						}
					}catch(NullPointerException e){
						throw new RuntimeException(e.getMessage(), e);
					}catch(IOException e){
						throw new RuntimeException(e.getMessage(), e);
					}
				});
				th.start();
				th.join();
			}catch(InterruptedException e1){
			}
			ArchiveEntry entry = null;
			while((entry = tais.getNextEntry()) != null){
				if (p.equals(entry)) entries.add(entry);
			}
		}catch(IOException ex){
			throw new RuntimeException(ex.getMessage(), ex);
		}
		return entries;
	}
	/**
	 * Predicateファイル展開.
	 * @param targzPath targzPath tar and gzip ファイルパス、 *.tar.gz
	 * @param dirPath 展開先パス
	 * @param p Predicate<ArchiveEntry> 展開するファイルのArchiveEntry の Predicate
	 */
	public static void predicateOpen(String targzPath, String dirPath, Predicate<ArchiveEntry> p){
		try(	FileInputStream fis = new FileInputStream(targzPath);
				GzipCompressorInputStream gin = new GzipCompressorInputStream(fis);
				PipedOutputStream pos = new PipedOutputStream();
				PipedInputStream pin = new PipedInputStream();
				TarArchiveInputStream tais = new TarArchiveInputStream(pin)
		){
			pin.connect(pos);
			try{
				Thread th = new Thread(()->{
					try{
						int size = 0;
						byte[] buf = new byte[1024];
						while((size = gin.read(buf)) >= 0){
							pos.write(buf, 0, size);
							pos.flush();
						}
					}catch(NullPointerException e){
						throw new RuntimeException(e.getMessage(), e);
					}catch(IOException e){
						throw new RuntimeException(e.getMessage(), e);
					}
				});
				th.start();
				th.join();
			}catch(InterruptedException e1){
			}
			ArchiveEntry entry = null;
			while((entry = tais.getNextEntry()) != null){
				if (p.test(entry) && !entry.isDirectory()){
					String[] names = entry.getName().split("/");
					try(FileOutputStream fos = new FileOutputStream(dirPath + "/" + names[names.length-1]);
						BufferedOutputStream bos = new BufferedOutputStream(fos)){
						int size = 0;
						byte[] buf = new byte[1024];
						while((size = tais.read(buf)) > 0){
							bos.write(buf, 0, size);
						}
					}catch(IOException e){
						throw new RuntimeException(e.getMessage(), e);
					}
				}
			}
		}catch(IOException ex){
			throw new RuntimeException(ex.getMessage(), ex);
		}
	}
	/**
	 * GZIP解凍実行.
	 * @param gzipPath gzip ファイルPATH   *.gz
	 * @param dirPath 展開先ディレクトリPATH
	 */
	public static void openGz(String gzipPath, String dirPath){
		String tarname = gzipPath.substring(gzipPath.lastIndexOf("/") + 1).replaceAll("\\.gz$", "");
		try(FileInputStream fis = new FileInputStream(gzipPath);GzipCompressorInputStream gin = new GzipCompressorInputStream(fis);
			FileOutputStream  fos = new FileOutputStream(dirPath + "/" + tarname)
		){
			int size = 0;
			byte[] buf = new byte[1024];
			while((size = gin.read(buf)) > 0){
				fos.write(buf, 0, size);
			}
		}catch(IOException ex){
			throw new RuntimeException(ex.getMessage(), ex);
		}
	}
}
