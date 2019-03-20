package uk.gov.hmcts.reform.sscs.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static uk.gov.hmcts.reform.sscs.ccd.util.CaseDataUtils.buildCaseData;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import uk.gov.hmcts.reform.pdf.service.client.PDFServiceClient;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;

public class SscsGeneratePdfServiceTest {

    private SscsGeneratePdfService service;

    private static final String TEMPLATE_PATH = "/templates/non_compliant_case_letter_template.html";

    @Mock
    private PDFServiceClient pdfServiceClient;

    SscsCaseData caseData = buildCaseData();

    @Before
    public void setup() {
        initMocks(this);
        service = new SscsGeneratePdfService(pdfServiceClient);
    }

    @Test
    public void generateValidPdf() {
        byte[] expected = {};
        given(pdfServiceClient.generateFromHtml(any(byte[].class), any())).willReturn(expected);

        Map<String, String> notificationPlaceholders = new HashMap<>();

        service.generatePdf(TEMPLATE_PATH, caseData, 1L, notificationPlaceholders);

        verify(pdfServiceClient).generateFromHtml(any(), any());
    }
}
