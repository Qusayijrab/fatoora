package com.fatoora.invoicing.importing;

import com.fatoora.invoicing.importing.ImportService.Report;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CSV import. The body is the file's text. dryRun (the default) only checks the rows;
 * dryRun=false writes them, all or nothing.
 */
@RestController
public class ImportController {

    private final ImportService imports;

    public ImportController(ImportService imports) {
        this.imports = imports;
    }

    @PostMapping(value = "/api/v1/clients/import", consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    public Report clients(@RequestBody String csv, @RequestParam(defaultValue = "true") boolean dryRun) {
        return imports.importClients(csv, dryRun);
    }

    @PostMapping(value = "/api/v1/products/import", consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    public Report products(@RequestBody String csv, @RequestParam(defaultValue = "true") boolean dryRun) {
        return imports.importProducts(csv, dryRun);
    }
}
