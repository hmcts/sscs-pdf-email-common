package uk.gov.hmcts.reform.sscs.service.conversion;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.sscs.docmosis.config.TemplateWelshMonthConfig;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LocalDateToWelshStringConverterTest {

    @Mock
    private TemplateWelshMonthConfig templateWelshMonthConfig;

    @InjectMocks
    private LocalDateToWelshStringConverter localDateToWelshStringConverter;
    private static Map<String, String> welshMonth;

    @BeforeClass
    public static void before() {
        welshMonth = new HashMap<>();
        welshMonth.put("1","Ionawr");
        welshMonth.put("2", "Chwefror");
        welshMonth.put("3", "Mawrth");
        welshMonth.put("4", "Ebrill");
        welshMonth.put("5", "Mai");
        welshMonth.put("6", "Mehefin");
        welshMonth.put("7", "Gorffennaf");
        welshMonth.put("8", "Awst");
        welshMonth.put("9", "Medi");
        welshMonth.put("10", "Hydref");
        welshMonth.put("11", "Tachwedd");
        welshMonth.put("12", "Rhagfyr");
    }

    @Before
    public void setUp() {
        when(templateWelshMonthConfig.getWelshMonths()).thenReturn(welshMonth);
    }

    @Test
    public void testLocalDateConvertedToWelsh() {
        String localDate = "2020-12-27";
        String welshDate = localDateToWelshStringConverter.convert(localDate);
        Assert.assertEquals("27 Rhagfyr 2020", welshDate);
    }

    @Test
    public void testLocalDateTimeConvertedToWelsh() {
        String localDate = "2020-12-27T17:13:21.569";
        String welshDate = localDateToWelshStringConverter.convertDateTime(localDate);
        Assert.assertEquals("27 Rhagfyr 2020", welshDate);
    }
}
