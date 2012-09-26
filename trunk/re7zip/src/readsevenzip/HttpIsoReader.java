/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package readsevenzip;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.sevenzipjbinding.ArchiveFormat;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.ISevenZipInArchive;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;

/**
 *
 * @author wael
 */
public class HttpIsoReader {

    private static void usage() {
        String archive_types = "";
        String archive_type = "";

        System.out.println("\nUsage:    java -jar re7zip.jar [OPTIONS]\n");
        System.out.println("Options:");
        System.out.println("          /t  -t    archive filetype:");
        
        for (ArchiveFormat archive_format : ArchiveFormat.values()) {
            archive_type = archive_format.toString().toLowerCase();
            if (archive_types.length() == 0) {
                archive_types = "                      " + archive_type;
            } else if (archive_types.length() < 70) {
                archive_types = archive_types + ", " + archive_type;
            } else {
                System.out.println(archive_types + ", " + archive_type + ",");
                archive_types = "";
            }
        }
        if (archive_types.length() != 0) {
            System.out.println(archive_types);
        }
        
        System.out.println("          /a  -a    archive filename or URL location of archive");
        System.out.println("          /e  -e    filename to extract out of the archive");
        System.out.println("          /o  -o    output filename for the extracted file\n");
        System.out.println("Example:");
        System.out.println("          java -jar re7zip.jar /t=iso\n"
                         + "                               /a=http://test.com/test.iso\n"
                         + "                               /e=some\\file.txt\n"
                         + "                               /o=file.txt\n");
        System.out.println("          java -jar re7zip.jar -t=iso\n"
                         + "                               -a=http://test.com/test.iso\n"
                         + "                               -e=some/file.txt\n"
                         + "                               -o=file.txt\n");
        
    }
    ISevenZipInArchive archive;

    public Boolean open(String URL) {
        return open(URL, null);
    }

    public Boolean open(String URL, ArchiveFormat type) {
        try {
            archive = SevenZip.openInArchive(type, new MonitorIInStream(new HttpIInStream(URL)));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public Boolean open(File file) {
        return open(file, null);
    }

    public Boolean open(File file, ArchiveFormat type) {
        try {
            archive = SevenZip.openInArchive(type,
                    new MonitorIInStream(
                    new RandomAccessFileInStream(
                    new RandomAccessFile(file, "r"))));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public void getFile(String sourceFilePath, String targetFilePath) throws IOException, SevenZipException {

        File f = new File(targetFilePath);
        final FileOutputStream fos = new FileOutputStream(f);
        try {
            int index = lookupIndex(sourceFilePath);
            if (index == -1) {
                throw new SevenZipException("File [" + sourceFilePath + "]is not found");
            }
            ISimpleInArchiveItem item = archive.getSimpleInterface().getArchiveItem(index);

            final int[] hash = new int[]{0};
            if (!item.isFolder()) {
                ExtractOperationResult result;
                final long[] sizeArray = new long[1];
                final Long size = item.getSize();

                result = item.extractSlow(new ISequentialOutStream() {

                    long loaded = 0;

                    public int write(byte[] data) throws SevenZipException {
                        try {
                            //                        hash[0] ^= Arrays.hashCode(data); // Consume data
                            //                        sizeArray[0] += data.length;
                            fos.write(data);
                            loaded += data.length;
                            
                            System.out.println(String.format("Percentage : %3s Reading %9s of %9s bytes",
                                    (loaded * 100) / size, loaded, size));

                            return data.length; // Return amount of consumed data
                        } catch (IOException ex) {
                            throw new SevenZipException(ex);
                        }
                    }
                });
                if (result == ExtractOperationResult.OK) {
                    fos.flush();
                    fos.close();
                } else {
                    System.err.println("Error extracting item: " + result);
                }
            }
        } catch (IOException ex) {
            throw new SevenZipException(ex);
        } finally {
            try {
                fos.close();
            } catch (IOException ex) {
                throw new SevenZipException(ex);
            }
        }
    }

    public void close() {
        try {
            archive.close();
        } catch (SevenZipException ex) {
            Logger.getLogger(HttpIsoReader.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private int lookupIndex(String sourceFilePath) throws SevenZipException {
        int numberOfItems = archive.getNumberOfItems();
        for (int i = 0; i < numberOfItems; i++) {
            if (archive.getSimpleInterface().getArchiveItem(i).getPath().equals(sourceFilePath)) {
                return i;
            }
        }
        return -1;
    }

    public static void main(String[] a) throws Exception {
        String archive_filename = null;
        ArchiveFormat archive_type = null;
        String extract_filename = null;
        String output_filename = null;

        for (String arg : a) {
            String arg_lower = arg.toLowerCase();
            
            if (arg_lower.startsWith("/t=") || arg_lower.startsWith("-t=")) {
                try {
                    archive_type = ArchiveFormat.valueOf(arg.substring(3).toUpperCase());
                } catch (IllegalArgumentException e) {
                    System.out.println("\nInvalid File Type : " + arg.substring(3).toUpperCase() + "\n");
                    usage();
                    System.exit(1);
                }
            }
            if (arg_lower.startsWith("/a=") || arg_lower.startsWith("-a=")) {
                archive_filename = arg.substring(3);
            }
            if (arg_lower.startsWith("/e=") || arg_lower.startsWith("-e=")) {
                extract_filename = arg.substring(3);
            }
            if (arg_lower.startsWith("/o=") || arg_lower.startsWith("-o=")) {
                output_filename = arg.substring(3);
            }

        }

        if (archive_type == null || archive_filename == null || extract_filename == null || output_filename == null) {
            usage();
            System.exit(1);
        }
        
                        
        long start = System.currentTimeMillis();

        HttpIsoReader reader = new HttpIsoReader();
        Boolean result = false;
        if (archive_filename.toLowerCase().startsWith("http://")) {
            System.out.println("Opening HTTP archive '" + archive_filename + "'.");
            result = reader.open(archive_filename, archive_type);
        } else {
            System.out.println("Opening FILE archive '" + archive_filename + "'.");
            try {
                result = reader.open(new File(archive_filename), archive_type);
            }
            catch(Exception e){
                System.out.println("Invalid '" + archive_type + "'  file '" + archive_filename + "'.");
                usage();
                System.exit(1);
            }
        }
        if(result){
            System.out.println("Archive '" + archive_filename + "' is open.");
        }
        else{
            System.out.println("Failed to open archive '" + archive_filename + "'.");
            System.exit(1);
        }
        
        
        if (System.getProperty("os.name").toLowerCase().indexOf("win") >= 0) {
            /**
             * Replace all forward slashes with backward slashes when run from Windows.
             * Also remove all slashes as the beginning.
             */
            extract_filename = extract_filename.replaceAll("/", "\\\\").replaceAll("^\\\\*", "");
        } else {
            /**
             * Replace all backward slashes with forward slashes when run from other operating systems.
             * Also remove all slashes as the beginning.
             */
            extract_filename = extract_filename.replaceAll("\\\\", "/").replaceAll("^/*", "");
        }
        
        System.out.println("Extracting file '" + extract_filename + "' ...");
        
        try {
            reader.getFile(extract_filename, output_filename);
            reader.close();
        }
        catch(Exception e){
                System.out.println("Filename '" + extract_filename + "' is not found in the archive.");
                System.exit(1);
        }
        
        long end = System.currentTimeMillis();
        long time = (end - start) / 1000;

        if (archive_filename.toLowerCase().startsWith("http://")) {
            System.out.println("Done. Downloaded in " + (time / 60) + " minutes and " + (time % 60) + " seconds.");
        } else {
            System.out.println("Done. Processed in " + (time / 60) + " minutes and " + (time % 60) + " seconds.");
        }
        
        System.exit(0);
    }
}