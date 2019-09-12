package uk.gov.hmcts.reform.sscs.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static uk.gov.hmcts.reform.sscs.ccd.util.CaseDataUtils.buildCaseData;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import uk.gov.hmcts.reform.pdf.service.client.PDFServiceClient;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;

@RunWith(JUnitParamsRunner.class)
public class SscsGeneratePdfServiceTest {

    private SscsGeneratePdfService service;

    private static final List<String> getTemplatePaths() {
        return Arrays.asList(
            "/templates/strike_out_letter_template.html"
        );
    }

    @Mock
    private PDFServiceClient pdfServiceClient;

    SscsCaseData caseData = buildCaseData();

    @Before
    public void setup() {
        initMocks(this);
        service = new SscsGeneratePdfService(pdfServiceClient);
    }

    @Test
    @Parameters(method = "getTemplatePaths")
    public void generateValidPdf(String templatePath) {
        byte[] expected = {};
        given(pdfServiceClient.generateFromHtml(any(byte[].class), any())).willReturn(expected);

        Map<String, String> notificationPlaceholders = new HashMap<>();

        service.generatePdf(templatePath, caseData, 1L, notificationPlaceholders);

        verify(pdfServiceClient).generateFromHtml(any(), any());
    }
}
