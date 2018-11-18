package nz.govt.natlib.tools.sip.pdf

import groovy.util.logging.Slf4j
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import org.apache.pdfbox.text.PDFTextStripper

import java.util.regex.Matcher
import java.util.regex.Pattern

@Slf4j
class PdfInformationExtractor {

    static Map<String, String> extractMetadata(File pdfFile) {
        Map<String, String> metadataMap = [ : ]
        PDDocument pdDocument = PDDocument.load(pdfFile)
        PDDocumentInformation info = pdDocument.getDocumentInformation()

        metadataMap.put("Page Count", "${pdDocument.getNumberOfPages()}")

        metadataMap.put("Title", "${info.getTitle()}")
        metadataMap.put("Author", "${info.getAuthor()}")
        metadataMap.put("Subject", "${info.getSubject()}")
        metadataMap.put("Keywords", "${info.getKeywords()}")
        metadataMap.put("Creator", "${info.getCreator()}")
        metadataMap.put("Producer", "${info.getProducer()}")
        metadataMap.put("Creation Date", "${info.getCreationDate()}")
        metadataMap.put("Modification Date", "${info.getModificationDate()}")
        metadataMap.put("Trapped", "${info.getTrapped()}")

        pdDocument.close()
        return metadataMap
    }

    static String extractText(File pdfFile) {
        PDDocument pdDocument = PDDocument.load(pdfFile)
        PDFTextStripper stripper = new PDFTextStripper()

        String text = stripper.getText(pdDocument)
        pdDocument.close()

        return text
    }

    static List<String> matchText(File pdfFile, String regexPattern) {
        log.info("matchText regexPattern=${regexPattern} for pdfFile=${pdfFile.getCanonicalPath()}")
        PDDocument pdDocument = PDDocument.load(pdfFile)
        PDFTextStripper stripper = new PDFTextStripper()

        String text = stripper.getText(pdDocument)

        List<String> matchingLines = [ ]
        Pattern pattern = Pattern.compile(regexPattern)
        text.eachLine { String line ->
            Matcher matcher = pattern.matcher(line)
            if (matcher.find()) {
                log.info("Found match for regexPattern=${regexPattern} in line=${line}")
                matchingLines.add(line)
            }
        }
        pdDocument.close()

        return matchingLines
    }

}