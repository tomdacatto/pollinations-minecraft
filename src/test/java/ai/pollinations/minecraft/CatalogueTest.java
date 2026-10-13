package ai.pollinations.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogueTest {

    private static final String LIST = "["
            + "{\"name\":\"amazon/nova-micro-v1\",\"aliases\":[\"nova\"],\"paid_only\":false,"
            + "\"health\":{\"status\":\"healthy\"}},"
            + "{\"name\":\"vendor/paid-model\",\"paid_only\":true,\"health\":{\"status\":\"healthy\"}},"
            + "{\"name\":\"vendor/broken-model\",\"paid_only\":null,\"health\":{\"status\":\"down\"}}"
            + "]";

    @Test
    void healthyFreeModelHasNoWarnings() {
        assertEquals(List.of(), Catalogue.warnings(LIST, "amazon/nova-micro-v1", "text"));
    }

    @Test
    void aliasesAndCaseCountAsTheSameModel() {
        assertEquals(List.of(), Catalogue.warnings(LIST, "NOVA", "text"));
    }

    @Test
    void paidOnlyModelIsFlagged() {
        List<String> warnings = Catalogue.warnings(LIST, "vendor/paid-model", "text");

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("paid-only"));
    }

    @Test
    void downModelIsFlaggedEvenWithANullPaidField() {
        List<String> warnings = Catalogue.warnings(LIST, "vendor/broken-model", "image");

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("reporting down"));
    }

    @Test
    void unknownModelIsFlagged() {
        List<String> warnings = Catalogue.warnings(LIST, "no/such-model", "image");

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("not in the live list"));
    }

    @Test
    void unreadableListIsReportedNotThrown() {
        List<String> warnings = Catalogue.warnings("<html>oops</html>", "amazon/nova-micro-v1", "text");

        assertEquals(1, warnings.size());
    }
}
