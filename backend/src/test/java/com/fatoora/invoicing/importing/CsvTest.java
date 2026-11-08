package com.fatoora.invoicing.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvTest {

    @Test
    void readsQuotesCommasAndLineBreaksInsideFields() {
        var rows = Csv.parse("name,notes\r\n\"Petra, Ltd\",\"Says \"\"hi\"\"\nand bye\"\r\nWadi Rum,\n");
        assertThat(rows).containsExactly(
                List.of("name", "notes"),
                List.of("Petra, Ltd", "Says \"hi\"\nand bye"),
                List.of("Wadi Rum", ""));
    }

    @Test
    void handlesExcelsByteOrderMarkSemicolonsAndArabic() {
        var rows = Csv.parse("﻿الاسم;البريد الإلكتروني\nشركة الندى;info@nada.jo\n\n");
        assertThat(rows).containsExactly(List.of("الاسم", "البريد الإلكتروني"), List.of("شركة الندى", "info@nada.jo"));
    }

    @Test
    void anUnclosedQuoteIsAnError() {
        assertThatThrownBy(() -> Csv.parse("name\n\"broken")).isInstanceOf(IllegalArgumentException.class);
    }
}
