package com.rooter.aipoc.document

import kr.dogfoot.hwplib.reader.HWPReader
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod as HwpTextExtractMethod
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor as HwpTextExtractor
import kr.dogfoot.hwpxlib.reader.HWPXReader
import kr.dogfoot.hwpxlib.tool.textextractor.TextExtractMethod as HwpxTextExtractMethod
import kr.dogfoot.hwpxlib.tool.textextractor.TextExtractor as HwpxTextExtractor
import kr.dogfoot.hwpxlib.tool.textextractor.TextMarks
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayInputStream
import java.io.File

class UnsupportedDocumentTypeException(extension: String) :
    RuntimeException("지원하지 않는 파일 형식입니다: .$extension (pdf, docx, hwp, hwpx만 지원합니다)")

class DocumentParseException(fileName: String, cause: Throwable) :
    RuntimeException("문서에서 텍스트를 추출하지 못했습니다: $fileName", cause)

/**
 * 업로드된 시험범위/교재 파일에서 순수 텍스트만 뽑아내는 유틸.
 * 추출된 텍스트는 AI 프롬프트의 <SOURCE_MATERIAL> 블록에 데이터로만 들어간다.
 */
object DocumentTextExtractor {

    private const val MAX_CHARS = 15_000

    fun extract(bytes: ByteArray, fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val rawText = try {
            when (extension) {
                "pdf" -> extractPdf(bytes)
                "docx" -> extractDocx(bytes)
                "hwp" -> extractHwp(bytes)
                "hwpx" -> extractHwpx(bytes)
                else -> throw UnsupportedDocumentTypeException(extension)
            }
        } catch (e: UnsupportedDocumentTypeException) {
            throw e
        } catch (e: Exception) {
            throw DocumentParseException(fileName, e)
        }

        return truncate(rawText.trim())
    }

    private fun extractPdf(bytes: ByteArray): String =
        Loader.loadPDF(bytes).use { document -> PDFTextStripper().getText(document) }

    private fun extractDocx(bytes: ByteArray): String =
        XWPFDocument(ByteArrayInputStream(bytes)).use { document ->
            XWPFWordExtractor(document).use { it.text }
        }

    private fun extractHwp(bytes: ByteArray): String {
        val hwpFile = HWPReader.fromInputStream(ByteArrayInputStream(bytes))
        return HwpTextExtractor.extract(hwpFile, HwpTextExtractMethod.InsertControlTextBetweenParagraphText)
    }

    private fun extractHwpx(bytes: ByteArray): String {
        // hwpxlib는 zip 기반이라 실제 File 경로만 받는다 (InputStream 오버로드 없음).
        val tempFile = File.createTempFile("aipoc-upload-", ".hwpx")
        return try {
            tempFile.writeBytes(bytes)
            val hwpxFile = HWPXReader.fromFile(tempFile)
            HwpxTextExtractor.extract(
                hwpxFile,
                HwpxTextExtractMethod.InsertControlTextBetweenParagraphText,
                false,
                TextMarks()
            )
        } finally {
            tempFile.delete()
        }
    }

    private fun truncate(text: String): String =
        if (text.length <= MAX_CHARS) text else text.take(MAX_CHARS) + "\n...(이하 생략)"
}
