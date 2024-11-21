package iped.parsers.misc;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ThumbcacheParser extends AbstractParser {

    private static final long serialVersionUID = 1L;
    private List<byte[]> extractedImages = new ArrayList<>();
    private List<String> extractedImageNames = new ArrayList<>();

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return MediaType.set("application/x-thumbcache");
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException {
        extractedImages.clear();
        extractedImageNames.clear();

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
            new ParsingEmbeddedDocumentExtractor(context));

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        try (TemporaryResources tmp = new TemporaryResources();
             TikaInputStream tis = TikaInputStream.get(stream, tmp)) {

            File file = tis.getFile();
            String outputDir = file.getParent();
            String imagesDir = outputDir + File.separator + "images";

            File imagesDirectory = new File(imagesDir);
            if (!imagesDirectory.exists()) {
                imagesDirectory.mkdirs();
            }

            try (FileInputStream fis = new FileInputStream(file)) {
                parseThumbcacheFile(fis, xhtml, imagesDir);
            }

            for (int i = 0; i < extractedImages.size(); i++) {
                if (extractor.shouldParseEmbedded(metadata)) {
                    Metadata imageMetadata = new Metadata();
                    String ext = detectImageExtension(extractedImages.get(i));
                    imageMetadata.set(Metadata.CONTENT_TYPE, "image/" + ext);
                    imageMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, extractedImageNames.get(i));

                    try (ByteArrayInputStream imageStream = new ByteArrayInputStream(extractedImages.get(i))) {
                        extractor.parseEmbedded(imageStream, xhtml, imageMetadata, true);
                    }
                }
            }

        } catch (IOException e) {
            xhtml.characters("Error processing thumbcache file: " + e.getMessage() + "\n");
        } finally {
            xhtml.endDocument();
        }
    }

    private void parseThumbcacheFile(InputStream stream, XHTMLContentHandler xhtml, String imagesDir) throws IOException, SAXException {
        byte[] buffer = new byte[56];

        ByteBuffer fileHeader = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        stream.read(fileHeader.array());

        String signature = new String(fileHeader.array(), 0, 4);
        if (!"CMMM".equals(signature)) {
            xhtml.characters("Error processing cache file: Invalid header signature; expected 'CMMM'.\n");
            return;
        }

        int formatVersion = fileHeader.getInt(4);
        int cacheType = fileHeader.getInt(8);
        int firstCacheEntryOffset = fileHeader.getInt(12);
        int firstAvailableCacheEntryOffset = fileHeader.getInt(16);
        int numberOfCacheEntries = fileHeader.getInt(20);

        xhtml.startElement("pre");
        xhtml.characters("Cache file format version      : " + formatVersion + "\n");
        xhtml.characters("Cache type                    : " + cacheType + "\n");
        xhtml.characters("Offset to first cache entry    : " + firstCacheEntryOffset + "\n");
        xhtml.characters("Offset to first available entry: " + firstAvailableCacheEntryOffset + "\n");
        xhtml.characters("Number of cache entries        : " + numberOfCacheEntries + "\n");

        String windowsVersion = "";
        switch (formatVersion) {
            case 20:
                windowsVersion = "Windows Vista";
                break;
            case 21:
                windowsVersion = "Windows 7";
                break;
            case 30:
                windowsVersion = "Windows 8.0";
                break;
            case 31:
                windowsVersion = "Windows 8.1";
                break;
            case 32:
                windowsVersion = "Windows 10 / 11";
                break;
            default:
                windowsVersion = "Unknown version";
        }
        xhtml.characters("Seen on Windows version        : " + windowsVersion + "\n");
        xhtml.endElement("pre");

        while (stream.read(buffer) == buffer.length) {
            ByteBuffer bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

            String entrySignature = new String(buffer, 0, 4);

            if (!"CMMM".equals(entrySignature)) {
                continue;
            }

            int entrySize = bb.getInt(4);
            long entryHash = bb.getLong(8);
            int identifierStringSize = bb.getInt(16);
            int paddingSize = bb.getInt(20);
            int dataSize = bb.getInt(24);
            long dataChecksum = bb.getLong(40);
            long headerChecksum = bb.getLong(48);

            if (dataSize > 0 && identifierStringSize > 0) {
                byte[] identifierBytes = new byte[identifierStringSize];
                stream.read(identifierBytes);
                String identifierString = new String(identifierBytes, StandardCharsets.UTF_16LE);

                xhtml.startElement("pre");
                xhtml.characters("Entry hash                    : 0x" + Long.toHexString(entryHash) + "\n");
                xhtml.characters("Entry size                    : " + entrySize + "\n");
                xhtml.characters("Identifier string             : " + identifierString + "\n");
                xhtml.characters("Data size                     : " + dataSize + "\n");
                xhtml.characters("Data checksum                 : 0x" + Long.toHexString(dataChecksum) + "\n");
                xhtml.characters("Header checksum               : 0x" + Long.toHexString(headerChecksum) + "\n");
                xhtml.endElement("pre");
            }

            if (paddingSize > 0) {
                stream.skip(paddingSize);
            }

            if (dataSize > 0) {
                byte[] imageData = new byte[dataSize];
                stream.read(imageData);

                String ext = detectImageExtension(imageData);
                String fileName = "thumb_" + Long.toHexString(entryHash) + "." + ext;

                saveImage(imagesDir, fileName, imageData);

                extractedImages.add(imageData);
                extractedImageNames.add(fileName);
            }
        }
    }

    private String detectImageExtension(byte[] data) {
        if (data.length >= 4) {
            if (data[0] == (byte) 0x42 && data[1] == (byte) 0x4D) {
                return "bmp";
            } else if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) {
                return "jpg";
            } else if (data[0] == (byte) 0x89 && data[1] == (byte) 0x50) {
                return "png";
            }
        }
        return "img";
    }

    private void saveImage(String imagesDir, String fileName, byte[] imageData) {
        try (FileOutputStream fos = new FileOutputStream(imagesDir + File.separator + fileName)) {
            fos.write(imageData);
            System.out.println("Saved image: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
