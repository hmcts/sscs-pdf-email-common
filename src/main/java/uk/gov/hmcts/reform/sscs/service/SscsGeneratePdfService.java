package uk.gov.hmcts.reform.sscs.service;

import static uk.gov.hmcts.reform.sscs.config.PdfTemplateConstants.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.pdf.service.client.PDFServiceClient;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.domain.pdf.PdfWrapper;
import uk.gov.hmcts.reform.sscs.exception.PdfGenerationException;

@Service
@Slf4j
public class SscsGeneratePdfService {
    private static final String RPC = "rpc_";
    private PDFServiceClient pdfServiceClient;

    @Autowired
    public SscsGeneratePdfService(PDFServiceClient pdfServiceClient) {
        this.pdfServiceClient = pdfServiceClient;
    }

    public byte[] generatePdf(String templatePath, SscsCaseData sscsCaseData, Long caseDetailsId, Map<String, String> notificationPlaceholders) {
        byte[] template;
        try {
            template = getTemplate(templatePath);
        } catch (IOException e) {
            throw new PdfGenerationException("Error getting template", e);
        }

        PdfWrapper pdfWrapper = PdfWrapper.builder()
            .sscsCaseData(sscsCaseData)
            .ccdCaseId(caseDetailsId)
            .currentDate(LocalDate.now())
            .build();

        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("PdfWrapper", pdfWrapper);
        placeholders.put(LETTER_ADDRESS_LINE_1, notificationPlaceholders.get(LETTER_ADDRESS_LINE_1));
        placeholders.put(LETTER_ADDRESS_LINE_2, notificationPlaceholders.get(LETTER_ADDRESS_LINE_2));
        placeholders.put(LETTER_ADDRESS_LINE_3, notificationPlaceholders.get(LETTER_ADDRESS_LINE_3));
        placeholders.put(LETTER_ADDRESS_LINE_4, notificationPlaceholders.get(LETTER_ADDRESS_LINE_4));
        placeholders.put(LETTER_ADDRESS_POSTCODE, notificationPlaceholders.get(LETTER_ADDRESS_POSTCODE));
        placeholders.put(LETTER_NAME, notificationPlaceholders.get(LETTER_NAME));
        placeholders.put(RPC + REGIONAL_OFFICE_NAME_LITERAL, notificationPlaceholders.get(REGIONAL_OFFICE_NAME_LITERAL));
        placeholders.put(RPC + SUPPORT_CENTRE_NAME_LITERAL, notificationPlaceholders.get(SUPPORT_CENTRE_NAME_LITERAL));
        placeholders.put(RPC + ADDRESS_LINE_LITERAL, notificationPlaceholders.get(ADDRESS_LINE_LITERAL));
        placeholders.put(RPC + TOWN_LITERAL, notificationPlaceholders.get(TOWN_LITERAL));
        placeholders.put(RPC + COUNTY_LITERAL, notificationPlaceholders.get(COUNTY_LITERAL));
        placeholders.put(RPC + POSTCODE_LITERAL, notificationPlaceholders.get(POSTCODE_LITERAL));
        placeholders.put(RPC + PHONE_NUMBER, notificationPlaceholders.get(PHONE_NUMBER));

        return pdfServiceClient.generateFromHtml(template, placeholders);
    }

    private byte[] getTemplate(String templatePath) throws IOException {
        InputStream in = getClass().getResourceAsStream(templatePath);
        return IOUtils.toByteArray(in);
    }
}
