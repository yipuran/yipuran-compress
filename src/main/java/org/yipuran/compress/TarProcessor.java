package org.yipuran.compress;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.yipuran.file.FileCollection;

/**
 * tar 圧縮／解凍プロセッサ.
 * <PRE>
 * （規則）
 *     tarファイル名拡張子 → .tar
 * （圧縮）
 * Supplier<Collection<FileCollection>> = ファイルコレクション（FileCollection）で指定する対象を
 * Collection<String> compress(String targzPath) で圧縮する。
 * メソッド戻り値は、tarエントリ名 Collection
 * （展開）
 * void decompress(String targzPath, String dirPath) で展開する。
 * </PRE>
 */
public interface TarProcessor extends Supplier<Collection<FileCollection>>{

	/**
	 * tar 圧縮実行.
	 * <PRE>
	 * Supplier で渡す FileCollection の渡し方で単一か複数か決まる。
	 * 例１）
	 *    // targetPath配下を圧縮対象にする場合
	 *    List<FileCollection> fileCollections =
	 *    Arrays.stream(new File(targetPath).listFiles()).map(e->FileCollection.of(e.getAbsolutePath())).collect(Collectors.toList());
	 *    TarProcessor processor = ()->fileCollections;
	 *    Collection<String> entries = processor.compress(tarPath);
	 *
	 * 例２）
	 *    // １つのディレクトリツリーで圧縮
	 *    FileCollection fileCollection = FileCollection.of(targetPath);
	 *    TarProcessor processor = ()->Arrays.asList(fileCollection);
	 *    Collection<String> entries = processor.compress(tarPath);
	 *
	 * </PRE>
	 * @param tarPath 作成する tar ファイルパス、 *.tar
	 * @return tarエントリ名 Collection
	 */
	public default Collection<String> compress(String tarPath){
		Collection<String> entries = new ArrayList<>();
		// tar 生成
		try(FileOutputStream out = new FileOutputStream(tarPath);TarArchiveOutputStream taos = new TarArchiveOutputStream(out)){
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
		return entries;
	}

	/**
	 * tar ファイル展開.
	 * @param tarPath tar ファイルパス、 *.tar
	 * @param dirPath 展開先ディレクトリPATH
	 * @return 展開された tar エントリ名
	 */
	public static Collection<String> decompress(String tarPath, String dirPath){
		Collection<String> entries = new ArrayList<>();
		// tar 展開
		try(FileInputStream fis = new FileInputStream(tarPath); TarArchiveInputStream tais = new TarArchiveInputStream(fis)){
			ArchiveEntry entry = null;
			while((entry = tais.getNextEntry()) != null){
				File file = new File(dirPath + "/" + entry.getName());
				if (entry.isDirectory()){
					file.mkdirs();
					entries.add(entry.getName());
					continue;
				}
				if (!file.getParentFile().exists()){ file.getParentFile().mkdirs(); }
				try(FileOutputStream fos = new FileOutputStream(file); BufferedOutputStream bos = new BufferedOutputStream(fos)){
					int size = 0;
					byte[] buf = new byte[1024];
					while((size = tais.read(buf)) > 0){
						bos.write(buf, 0, size);
					}
				}
				entries.add(entry.getName());
			}
		}catch(IOException ex){
			throw new RuntimeException(ex.getMessage(), ex);
		}
		return entries;
	}
	/**
	 * エントリ名コレクション.
	 * @param tarPath tar ファイルパス、 *.tar
	 * @return Collection<String>
	 */
	public static Collection<String> viewPath(String tarPath){
		Collection<String> entries = new ArrayList<>();
		try(FileInputStream fis = new FileInputStream(tarPath); TarArchiveInputStream tais = new TarArchiveInputStream(fis)){
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
	 * @param tarPath tar ファイルパス、 *.tar
	 * @return Collection<ArchiveEntry>
	 */
	public static Collection<ArchiveEntry> entries(String tarPath){
		Collection<ArchiveEntry> entries = new ArrayList<>();
		try(FileInputStream fis = new FileInputStream(tarPath); TarArchiveInputStream tais = new TarArchiveInputStream(fis)){
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
	 * @param tarPath tarPath tar ファイルパス、 *.tar
	 * @param p ファイルのArchiveEntry の Predicate
	 * @return Collection<ArchiveEntry>
	 */
	public static Collection<ArchiveEntry> entries(String tarPath, Predicate<ArchiveEntry> p){
		Collection<ArchiveEntry> entries = new ArrayList<>();
		try(FileInputStream fis = new FileInputStream(tarPath); TarArchiveInputStream tais = new TarArchiveInputStream(fis)){
			ArchiveEntry entry = null;
			while((entry = tais.getNextEntry()) != null){
				if (p.equals(entry)) entries.add(entry);
			}
		}catch(IOException ex){
			throw new RuntimeException(ex.getMessage(), ex);
		}
		return entries;
	}
}
