package com.fatoora.invoicing.importing;

import com.fatoora.invoicing.audit.AuditService;
import com.fatoora.invoicing.client.Client;
import com.fatoora.invoicing.client.ClientDtos.ClientRequest;
import com.fatoora.invoicing.client.ClientRepository;
import com.fatoora.invoicing.client.ClientService;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.organization.OrganizationService;
import com.fatoora.invoicing.organization.Role;
import com.fatoora.invoicing.product.Product;
import com.fatoora.invoicing.product.ProductDtos.ProductRequest;
import com.fatoora.invoicing.product.ProductRepository;
import com.fatoora.invoicing.product.ProductService;
import com.fatoora.invoicing.tax.TaxRate;
import com.fatoora.invoicing.tax.TaxRateRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports clients and products from a CSV file (e.g. Excel → Save as → CSV UTF-8).
 * First a dry run reports every row (ok, skipped as a duplicate, or what is wrong);
 * then the import writes all the ok rows in one transaction, or nothing if any row
 * has an error. Rows go through the same validation and create logic as the forms.
 */
@Service
public class ImportService {

    public enum RowStatus { OK, SKIP, ERROR }

    public record Row(int line, String name, RowStatus status, List<String> messages) {}

    public record Report(boolean imported, int ok, int skipped, int errors, List<Row> rows) {}

    static final int MAX_ROWS = 1000;
    static final int MAX_CHARS = 1_000_000;

    private final ClientService clients;
    private final ClientRepository clientRepo;
    private final ProductService products;
    private final ProductRepository productRepo;
    private final TaxRateRepository taxRates;
    private final OrganizationService organizations;
    private final AuditService audit;
    private final Validator validator;

    public ImportService(ClientService clients, ClientRepository clientRepo, ProductService products,
            ProductRepository productRepo, TaxRateRepository taxRates, OrganizationService organizations,
            AuditService audit, Validator validator) {
        this.clients = clients;
        this.clientRepo = clientRepo;
        this.products = products;
        this.productRepo = productRepo;
        this.taxRates = taxRates;
        this.organizations = organizations;
        this.audit = audit;
        this.validator = validator;
    }

    // ---- clients ----

    private static final Map<String, String> CLIENT_COLUMNS = columns(Map.of(
            "name", List.of("name", "client", "client name", "company", "الاسم", "اسم العميل"),
            "email", List.of("email", "e-mail", "email address", "البريد الإلكتروني"),
            "phone", List.of("phone", "telephone", "mobile", "الهاتف"),
            "taxId", List.of("tax id", "taxid", "tax number", "vat number", "الرقم الضريبي"),
            "addressLine1", List.of("address", "address line 1", "address1", "العنوان"),
            "addressLine2", List.of("address line 2", "address2"),
            "city", List.of("city", "المدينة"),
            "region", List.of("region", "state", "governorate", "المحافظة"),
            "postalCode", List.of("postal code", "postcode", "zip", "الرمز البريدي"),
            "country", List.of("country", "country code", "الدولة")),
            Map.of("currency", List.of("currency", "العملة"), "notes", List.of("notes", "ملاحظات")));

    @Transactional
    public Report importClients(String csv, boolean dryRun) {
        TenantContext.require().requireRole(Role.ADMIN);
        List<Map<String, String>> records = read(csv, CLIENT_COLUMNS, "name");
        Set<String> seen = new HashSet<>();
        for (Client c : clientRepo.findAll()) {
            seen.add(clientKey(c.getName(), c.getEmail()));
        }
        List<Row> rows = new ArrayList<>();
        List<ClientRequest> toCreate = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            Map<String, String> r = records.get(i);
            ClientRequest req = new ClientRequest(r.get("name"), r.get("email"), r.get("phone"), r.get("taxId"),
                    r.get("addressLine1"), r.get("addressLine2"), r.get("city"), r.get("region"), r.get("postalCode"),
                    upper(r.get("country")), upper(r.get("currency")), r.get("notes"));
            List<String> problems = violations(req);
            String name = req.name() == null ? "" : req.name();
            if (!problems.isEmpty()) {
                rows.add(new Row(i + 2, name, RowStatus.ERROR, problems));
            } else if (!seen.add(clientKey(req.name(), req.email()))) {
                rows.add(new Row(i + 2, name, RowStatus.SKIP, List.of("A client with this "
                        + (req.email() == null ? "name" : "email") + " already exists")));
            } else {
                rows.add(new Row(i + 2, name, RowStatus.OK, List.of()));
                toCreate.add(req);
            }
        }
        return finish(rows, dryRun, () -> {
            toCreate.forEach(clients::create);
            audit.record("organization", TenantContext.require().organizationId(), "clients_imported",
                    "Imported " + toCreate.size() + " clients from a CSV file",
                    Map.of("count", toCreate.size()));
        });
    }

    private static String clientKey(String name, String email) {
        return email != null && !email.isBlank() ? "e:" + email.trim().toLowerCase(Locale.ROOT)
                : "n:" + (name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
    }

    // ---- products ----

    private static final Map<String, String> PRODUCT_COLUMNS = columns(Map.of(
            "name", List.of("name", "product", "product name", "item", "الاسم", "المنتج"),
            "description", List.of("description", "details", "الوصف"),
            "sku", List.of("sku", "code", "item code", "الرمز"),
            "unit", List.of("unit", "uom", "الوحدة"),
            "unitPrice", List.of("unit price", "price", "unitprice", "rate", "السعر"),
            "currency", List.of("currency", "العملة"),
            "tax", List.of("tax", "tax rate", "vat", "الضريبة")), Map.of());

    @Transactional
    public Report importProducts(String csv, boolean dryRun) {
        TenantContext.require().requireRole(Role.ADMIN);
        List<Map<String, String>> records = read(csv, PRODUCT_COLUMNS, "name");
        String orgCurrency = organizations.currentEntity().getDefaultCurrency();
        List<TaxRate> rates = taxRates.findByArchivedFalseOrderByNameAsc();
        Set<String> seen = new HashSet<>();
        for (Product p : productRepo.findAll()) {
            seen.add(productKey(p.getName(), p.getSku()));
        }
        List<Row> rows = new ArrayList<>();
        List<ProductRequest> toCreate = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            Map<String, String> r = records.get(i);
            List<String> problems = new ArrayList<>();
            UUID taxRateId = null;
            String tax = r.get("tax");
            if (tax != null) {
                TaxRate match = matchTaxRate(rates, tax);
                if (match == null) {
                    problems.add("tax: no active tax rate named or equal to \"" + tax + "\" (add it under Settings first)");
                } else {
                    taxRateId = match.getId();
                }
            }
            String currency = r.get("currency") == null ? orgCurrency : upper(r.get("currency"));
            String price = r.get("unitPrice") == null ? null : r.get("unitPrice").replace(",", "");
            ProductRequest req = new ProductRequest(r.get("name"), r.get("description"), r.get("sku"), r.get("unit"),
                    price, currency, taxRateId);
            problems.addAll(violations(req));
            if (problems.isEmpty()) {
                try {
                    Money.parse(req.unitPrice(), req.currency());
                } catch (ApiException e) {
                    problems.add("unitPrice: " + e.getMessage());
                }
            }
            String name = req.name() == null ? "" : req.name();
            if (!problems.isEmpty()) {
                rows.add(new Row(i + 2, name, RowStatus.ERROR, problems));
            } else if (!seen.add(productKey(req.name(), req.sku()))) {
                rows.add(new Row(i + 2, name, RowStatus.SKIP, List.of("A product with this "
                        + (req.sku() == null ? "name" : "SKU") + " already exists")));
            } else {
                rows.add(new Row(i + 2, name, RowStatus.OK, List.of()));
                toCreate.add(req);
            }
        }
        return finish(rows, dryRun, () -> {
            toCreate.forEach(products::create);
            audit.record("organization", TenantContext.require().organizationId(), "products_imported",
                    "Imported " + toCreate.size() + " products from a CSV file",
                    Map.of("count", toCreate.size()));
        });
    }

    private static String productKey(String name, String sku) {
        return sku != null && !sku.isBlank() ? "s:" + sku.trim().toLowerCase(Locale.ROOT)
                : "n:" + (name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
    }

    private static TaxRate matchTaxRate(List<TaxRate> rates, String value) {
        String v = value.trim().replace("%", "").trim();
        for (TaxRate t : rates) {
            if (t.getName().equalsIgnoreCase(value.trim())) {
                return t;
            }
        }
        try {
            BigDecimal percent = new BigDecimal(v);
            return rates.stream().filter(t -> t.getRate().compareTo(percent) == 0).findFirst().orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- shared ----

    private Report finish(List<Row> rows, boolean dryRun, Runnable create) {
        int ok = (int) rows.stream().filter(r -> r.status() == RowStatus.OK).count();
        int skipped = (int) rows.stream().filter(r -> r.status() == RowStatus.SKIP).count();
        int errors = rows.size() - ok - skipped;
        boolean write = !dryRun && errors == 0 && ok > 0;
        if (write) {
            create.run();
        }
        return new Report(write, ok, skipped, errors, rows);
    }

    /**
     * Rows as maps keyed by our field names. Header names are matched ignoring case,
     * spaces, '_' and '-' (so "Tax ID", "tax_id" and "taxId" all work), in English or Arabic.
     */
    private static List<Map<String, String>> read(String csv, Map<String, String> columns, String required) {
        if (csv == null || csv.isBlank()) {
            throw ApiException.badRequest("The file is empty");
        }
        if (csv.length() > MAX_CHARS) {
            throw ApiException.badRequest("The file is too large (at most 1 MB)");
        }
        List<List<String>> table;
        try {
            table = Csv.parse(csv);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("This doesn't look like a valid CSV file: " + e.getMessage());
        }
        if (table.size() < 2) {
            throw ApiException.badRequest("The file needs a header row and at least one data row");
        }
        if (table.size() - 1 > MAX_ROWS) {
            throw ApiException.badRequest("At most " + MAX_ROWS + " rows per file; split it into smaller files");
        }
        List<String> header = table.getFirst();
        Map<Integer, String> fieldAt = new HashMap<>();
        for (int c = 0; c < header.size(); c++) {
            String field = columns.get(normalize(header.get(c)));
            if (field != null) {
                fieldAt.putIfAbsent(c, field);
            }
        }
        if (!fieldAt.containsValue(required)) {
            throw ApiException.badRequest("The header row needs a \"" + required + "\" column. Found: " + String.join(", ", header));
        }
        List<Map<String, String>> out = new ArrayList<>();
        for (List<String> line : table.subList(1, table.size())) {
            Map<String, String> record = new HashMap<>();
            fieldAt.forEach((c, field) -> {
                if (c < line.size() && !line.get(c).isBlank()) {
                    record.put(field, line.get(c).trim());
                }
            });
            out.add(record);
        }
        return out;
    }

    private List<String> violations(Object request) {
        List<String> out = new ArrayList<>();
        for (ConstraintViolation<Object> v : validator.validate(request)) {
            out.add(v.getPropertyPath() + ": " + v.getMessage());
        }
        out.sort(String::compareTo);
        return out;
    }

    private static String upper(String s) {
        return s == null ? null : s.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalize(String header) {
        return header.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ");
    }

    @SafeVarargs
    private static Map<String, String> columns(Map<String, List<String>>... groups) {
        Map<String, String> out = new HashMap<>();
        for (Map<String, List<String>> g : groups) {
            g.forEach((field, aliases) -> {
                out.put(normalize(field.replaceAll("([a-z])([A-Z])", "$1 $2")), field);
                aliases.forEach(a -> out.put(normalize(a), field));
            });
        }
        return out;
    }
}
