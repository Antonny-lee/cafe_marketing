package com.cafe.dashboard.service;

import com.cafe.dashboard.entity.DailySales;
import com.cafe.dashboard.entity.Expense;
import com.cafe.dashboard.entity.FixedCost;
import com.cafe.dashboard.repository.DailySalesRepository;
import com.cafe.dashboard.repository.ExpenseRepository;
import com.cafe.dashboard.repository.FixedCostRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final DailySalesRepository dailySalesRepository;
    private final ExpenseRepository expenseRepository;
    private final FixedCostRepository fixedCostRepository;

    private static final List<String> DATE_HEADER_HINTS = List.of("날짜", "일자", "date");
    private static final List<String> AMOUNT_HEADER_HINTS = List.of("매출", "금액", "amount", "sales", "판매액");

    public record CategoryStat(String category, long amount, int percent) {}

    public record Overview(
            long todaySales,
            long monthSales,
            long monthExpense,
            long netProfit,
            long thisMonthToDate,
            long lastMonthSameDayToDate,
            Double paceChangePercent,
            long thisYearToDate,
            long lastYearSameMonthToDate,
            Double yoyChangePercent,
            List<CategoryStat> categoryBreakdown,
            String lastUploadInfo
    ) {
        private static final String[] CATEGORY_COLORS =
                {"#3E4A2A", "#A8462E", "#4A6FA5", "#B98A2E", "#7A5C99", "#C2703D", "#6B6B6B"};

        public String categoryColor(int index) {
            return CATEGORY_COLORS[index % CATEGORY_COLORS.length];
        }
    }

    public record ImportResult(int rowsImported, LocalDate from, LocalDate to, long totalAmount) {}

    public record MonthSummary(String yearMonth, long sales, long expense, long netProfit,
                                int salesBarPercent, int expenseBarPercent) {}

    public record HomeSummary(
            long todaySales,
            long vsYesterdayAmount,
            Integer vsYesterdayPercent,
            int vsYesterdayAbsPercent,
            String lastUploadInfo,
            long expenseCountThisMonth,
            long monthSales,
            long materialCost,
            long rentCost,
            long otherCost,
            long netProfit,
            long thisMonthToDate,
            long lastMonthSameDayToDate,
            Double paceChangePercent,
            long projectedMonthSales,
            List<Long> thisMonthCumulative,
            List<Long> lastMonthCumulative
    ) {}

    @Transactional
    public HomeSummary loadHomeSummary(String storeId) {
        ensureFixedCostsPosted(storeId);

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate lastMonthStart = monthStart.minusMonths(1);
        LocalDate lastMonthEnd = lastMonthStart.withDayOfMonth(lastMonthStart.lengthOfMonth());
        LocalDate lastMonthSameDay = lastMonthStart.plusDays(Math.min(today.getDayOfMonth(), lastMonthStart.lengthOfMonth()) - 1L);

        long todaySales = dailySalesRepository.findByStoreIdAndSaleDate(storeId, today).map(DailySales::getAmount).orElse(0L);
        long yesterdaySales = dailySalesRepository.findByStoreIdAndSaleDate(storeId, yesterday).map(DailySales::getAmount).orElse(0L);

        long vsYesterdayAmount = todaySales - yesterdaySales;
        Integer vsYesterday = yesterdaySales == 0 ? null : (int) Math.round((todaySales - yesterdaySales) * 100.0 / yesterdaySales);
        int vsYesterdayAbs = vsYesterday == null ? 0 : Math.abs(vsYesterday);

        long monthSales = dailySalesRepository.sumAmount(storeId, monthStart, today);
        long monthExpense = expenseRepository.sumAmount(storeId, monthStart, today);
        long netProfit = monthSales - monthExpense;
        long expenseCount = expenseRepository.countByStoreIdAndExpenseDateBetween(storeId, monthStart, today);

        List<ExpenseRepository.CategoryTotal> totals = expenseRepository.sumByCategory(storeId, monthStart, today);
        long materialCost = totals.stream().filter(t -> "재료 구입".equals(t.getCategory())).mapToLong(ExpenseRepository.CategoryTotal::getTotal).sum();
        long rentCost = totals.stream().filter(t -> "임대료".equals(t.getCategory())).mapToLong(ExpenseRepository.CategoryTotal::getTotal).sum();
        long otherCost = monthExpense - materialCost - rentCost;

        long lastMonthToDate = dailySalesRepository.sumAmount(storeId, lastMonthStart, lastMonthSameDay);
        Double paceChange = lastMonthToDate == 0 ? null : Math.round((monthSales - lastMonthToDate) * 1000.0 / lastMonthToDate) / 10.0;
        long projectedMonthSales = today.getDayOfMonth() == 0 ? 0 : monthSales * today.lengthOfMonth() / today.getDayOfMonth();

        List<Long> thisMonthCumulative = cumulativeDaily(storeId, monthStart, today);
        List<Long> lastMonthCumulative = cumulativeDaily(storeId, lastMonthStart, lastMonthEnd);

        String lastUploadInfo = dailySalesRepository.findByStoreIdAndSaleDateBetweenOrderBySaleDateDesc(storeId, monthStart.minusMonths(2), today)
                .stream()
                .filter(d -> "UPLOAD".equals(d.getSource()))
                .findFirst()
                .map(d -> d.getSaleDate().toString())
                .orElse(null);

        return new HomeSummary(todaySales, vsYesterdayAmount, vsYesterday, vsYesterdayAbs,
                lastUploadInfo, expenseCount,
                monthSales, materialCost, rentCost, otherCost, netProfit,
                monthSales, lastMonthToDate, paceChange, projectedMonthSales,
                thisMonthCumulative, lastMonthCumulative);
    }

    /** Running daily total for each day in [from, to], inclusive - missing days just carry the prior total forward. */
    private List<Long> cumulativeDaily(String storeId, LocalDate from, LocalDate to) {
        Map<LocalDate, Long> byDate = dailySalesRepository.findByStoreIdAndSaleDateBetweenOrderBySaleDateDesc(storeId, from, to)
                .stream()
                .collect(Collectors.toMap(DailySales::getSaleDate, DailySales::getAmount));

        List<Long> result = new ArrayList<>();
        long running = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            running += byDate.getOrDefault(d, 0L);
            result.add(running);
        }
        return result;
    }

    public List<MonthSummary> monthlyComparison(String storeId, int monthsBack) {
        LocalDate today = LocalDate.now();
        List<MonthSummary> raw = new ArrayList<>();

        for (int i = monthsBack - 1; i >= 0; i--) {
            LocalDate monthStart = today.minusMonths(i).withDayOfMonth(1);
            LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
            if (monthEnd.isAfter(today)) monthEnd = today;

            long sales = dailySalesRepository.sumAmount(storeId, monthStart, monthEnd);
            long expense = expenseRepository.sumAmount(storeId, monthStart, monthEnd);
            String label = monthStart.format(DateTimeFormatter.ofPattern("yyyy.MM"));
            raw.add(new MonthSummary(label, sales, expense, sales - expense, 0, 0));
        }

        long maxSales = raw.stream().mapToLong(MonthSummary::sales).max().orElse(1);
        long maxExpense = raw.stream().mapToLong(MonthSummary::expense).max().orElse(1);
        return raw.stream()
                .map(m -> new MonthSummary(m.yearMonth(), m.sales(), m.expense(), m.netProfit(),
                        maxSales == 0 ? 0 : (int) Math.round(100.0 * m.sales() / maxSales),
                        maxExpense == 0 ? 0 : (int) Math.round(100.0 * m.expense() / maxExpense)))
                .toList();
    }

    @Transactional
    public Overview loadOverview(String storeId) {
        ensureFixedCostsPosted(storeId);

        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate lastMonthStart = monthStart.minusMonths(1);
        LocalDate lastMonthSameDay = lastMonthStart.plusDays(Math.min(today.getDayOfMonth(), lastMonthStart.lengthOfMonth()) - 1L);
        LocalDate lastYearMonthStart = monthStart.minusYears(1);
        LocalDate lastYearSameDay = lastYearMonthStart.plusDays(Math.min(today.getDayOfMonth(), lastYearMonthStart.lengthOfMonth()) - 1L);

        long todaySales = dailySalesRepository.findByStoreIdAndSaleDate(storeId, today).map(DailySales::getAmount).orElse(0L);
        long monthSales = dailySalesRepository.sumAmount(storeId, monthStart, today);
        long monthExpense = expenseRepository.sumAmount(storeId, monthStart, today);
        long netProfit = monthSales - monthExpense;

        long lastMonthToDate = dailySalesRepository.sumAmount(storeId, lastMonthStart, lastMonthSameDay);
        Double paceChange = lastMonthToDate == 0 ? null : Math.round((monthSales - lastMonthToDate) * 1000.0 / lastMonthToDate) / 10.0;

        long lastYearToDate = dailySalesRepository.sumAmount(storeId, lastYearMonthStart, lastYearSameDay);
        Double yoyChange = lastYearToDate == 0 ? null : Math.round((monthSales - lastYearToDate) * 1000.0 / lastYearToDate) / 10.0;

        List<ExpenseRepository.CategoryTotal> totals = expenseRepository.sumByCategory(storeId, monthStart, today);
        long totalExpenseForPct = totals.stream().mapToLong(ExpenseRepository.CategoryTotal::getTotal).sum();
        List<CategoryStat> breakdown = totals.stream()
                .map(t -> new CategoryStat(t.getCategory(), t.getTotal(),
                        totalExpenseForPct == 0 ? 0 : (int) Math.round(100.0 * t.getTotal() / totalExpenseForPct)))
                .toList();

        String lastUploadInfo = dailySalesRepository.findByStoreIdAndSaleDateBetweenOrderBySaleDateDesc(storeId, monthStart.minusMonths(2), today)
                .stream()
                .filter(d -> "UPLOAD".equals(d.getSource()))
                .findFirst()
                .map(d -> d.getUploadedFile() + " · " + d.getSaleDate())
                .orElse(null);

        return new Overview(todaySales, monthSales, monthExpense, netProfit,
                monthSales, lastMonthToDate, paceChange,
                monthSales, lastYearToDate, yoyChange,
                breakdown, lastUploadInfo);
    }

    public List<DailySales> listDailySales(String storeId, LocalDate from, LocalDate to) {
        return dailySalesRepository.findByStoreIdAndSaleDateBetweenOrderBySaleDateDesc(storeId, from, to);
    }

    @Transactional
    public void deleteSale(String storeId, LocalDate date) {
        dailySalesRepository.deleteByStoreIdAndSaleDate(storeId, date);
    }

    @Transactional
    public void addManualSale(String storeId, LocalDate date, long amount) {
        DailySales sales = dailySalesRepository.findByStoreIdAndSaleDate(storeId, date).orElseGet(DailySales::new);
        sales.setStoreId(storeId);
        sales.setSaleDate(date);
        sales.setAmount(amount);
        if (sales.getSource() == null) sales.setSource("MANUAL");
        if (sales.getCreatedAt() == null) sales.setCreatedAt(LocalDateTime.now());
        dailySalesRepository.save(sales);
    }

    @Transactional
    public ImportResult importSalesFile(String storeId, MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        List<String[]> rows = filename.toLowerCase(Locale.ROOT).endsWith(".csv")
                ? parseCsv(file)
                : parseXlsx(file);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("파일에서 데이터를 찾지 못했습니다.");
        }

        String[] header = rows.get(0);
        int dateCol = findColumn(header, DATE_HEADER_HINTS);
        int amountCol = findColumn(header, AMOUNT_HEADER_HINTS);
        if (dateCol == -1 || amountCol == -1) {
            throw new IllegalArgumentException(
                    "날짜/매출 컬럼을 찾지 못했습니다. 헤더에 '날짜'(또는 date)와 '매출'/'금액'(또는 amount) 컬럼이 있어야 합니다. "
                            + "발견된 헤더: " + String.join(", ", header));
        }

        int imported = 0;
        LocalDate minDate = null, maxDate = null;
        long total = 0;

        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (dateCol >= row.length || amountCol >= row.length) continue;
            LocalDate date = parseFlexibleDate(row[dateCol]);
            Long amount = parseAmount(row[amountCol]);
            if (date == null || amount == null) continue;

            DailySales sales = dailySalesRepository.findByStoreIdAndSaleDate(storeId, date).orElseGet(DailySales::new);
            sales.setStoreId(storeId);
            sales.setSaleDate(date);
            sales.setAmount(amount);
            sales.setSource("UPLOAD");
            sales.setUploadedFile(filename);
            if (sales.getCreatedAt() == null) sales.setCreatedAt(LocalDateTime.now());
            dailySalesRepository.save(sales);

            imported++;
            total += amount;
            if (minDate == null || date.isBefore(minDate)) minDate = date;
            if (maxDate == null || date.isAfter(maxDate)) maxDate = date;
        }

        if (imported == 0) {
            throw new IllegalArgumentException("유효한 날짜/금액 데이터를 찾지 못했습니다. 날짜는 YYYY-MM-DD, 금액은 숫자 형식이어야 합니다.");
        }
        return new ImportResult(imported, minDate, maxDate, total);
    }

    @Transactional
    public void addExpense(String storeId, String category, String vendor, long amount, String paymentMethod,
                            String memo, LocalDate expenseDate, boolean recurring) {
        Long fixedCostId = null;
        if (recurring) {
            FixedCost fixedCost = new FixedCost();
            fixedCost.setStoreId(storeId);
            fixedCost.setCategory(category);
            fixedCost.setVendor(vendor);
            fixedCost.setAmount(amount);
            fixedCost.setPaymentMethod(paymentMethod);
            fixedCost.setDayOfMonth(expenseDate.getDayOfMonth());
            fixedCost.setMemo(memo);
            fixedCost.setActive("Y");
            fixedCost.setCreatedAt(LocalDateTime.now());
            fixedCostRepository.save(fixedCost);
            fixedCostId = fixedCost.getId();
        }

        Expense expense = new Expense();
        expense.setStoreId(storeId);
        expense.setCategory(category);
        expense.setVendor(vendor);
        expense.setAmount(amount);
        expense.setPaymentMethod(paymentMethod);
        expense.setMemo(memo);
        expense.setExpenseDate(expenseDate);
        expense.setIsFixedCost(recurring ? "Y" : "N");
        expense.setFixedCostId(fixedCostId);
        expense.setCreatedAt(LocalDateTime.now());
        expenseRepository.save(expense);
    }

    @Transactional
    public void ensureFixedCostsPosted(String storeId) {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        List<FixedCost> active = fixedCostRepository.findByStoreIdAndActiveOrderByDayOfMonth(storeId, "Y");

        for (FixedCost fc : active) {
            if (fc.getDayOfMonth() > today.getDayOfMonth()) continue;
            if (expenseRepository.existsByFixedCostIdAndExpenseDateBetween(fc.getId(), monthStart, today)) continue;

            LocalDate postDate = monthStart.withDayOfMonth(Math.min(fc.getDayOfMonth(), monthStart.lengthOfMonth()));
            Expense expense = new Expense();
            expense.setStoreId(storeId);
            expense.setCategory(fc.getCategory());
            expense.setVendor(fc.getVendor());
            expense.setAmount(fc.getAmount());
            expense.setPaymentMethod(fc.getPaymentMethod());
            expense.setMemo("고정비 자동 등록");
            expense.setExpenseDate(postDate);
            expense.setIsFixedCost("Y");
            expense.setFixedCostId(fc.getId());
            expense.setCreatedAt(LocalDateTime.now());
            expenseRepository.save(expense);
        }
    }

    public List<FixedCost> listFixedCosts(String storeId) {
        return fixedCostRepository.findByStoreIdAndActiveOrderByDayOfMonth(storeId, "Y");
    }

    @Transactional
    public void updateFixedCost(String storeId, Long id, String category, String vendor, long amount,
                                 String paymentMethod, int dayOfMonth, String memo) {
        FixedCost fixedCost = fixedCostRepository.findById(id)
                .filter(fc -> fc.getStoreId().equals(storeId))
                .orElseThrow(() -> new IllegalArgumentException("본인 매장의 고정비만 수정할 수 있습니다."));
        fixedCost.setCategory(category);
        fixedCost.setVendor(vendor);
        fixedCost.setAmount(amount);
        fixedCost.setPaymentMethod(paymentMethod);
        fixedCost.setDayOfMonth(dayOfMonth);
        fixedCost.setMemo(memo);
        fixedCostRepository.save(fixedCost);

        // The current month's auto-posted expense (see ensureFixedCostsPosted) was created from the
        // old values before this edit - without this, edits wouldn't show up until next month's posting.
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        for (Expense expense : expenseRepository.findByFixedCostIdAndExpenseDateBetween(id, monthStart, today)) {
            expense.setCategory(category);
            expense.setVendor(vendor);
            expense.setAmount(amount);
            expense.setPaymentMethod(paymentMethod);
            expense.setMemo(memo);
            expenseRepository.save(expense);
        }
    }

    public Page<Expense> listExpenses(String storeId, int page) {
        return expenseRepository.findByStoreIdOrderByExpenseDateDescIdDesc(storeId, PageRequest.of(page, 20));
    }

    private int findColumn(String[] header, List<String> hints) {
        for (int i = 0; i < header.length; i++) {
            String h = header[i] == null ? "" : header[i].toLowerCase(Locale.ROOT).trim();
            for (String hint : hints) {
                if (h.contains(hint.toLowerCase(Locale.ROOT))) return i;
            }
        }
        return -1;
    }

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    private LocalDate parseFlexibleDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String text = raw.trim();
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, fmt);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return null;
    }

    private Long parseAmount(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = raw.replaceAll("[^0-9.-]", "");
        if (cleaned.isBlank()) return null;
        try {
            return (long) Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String[]> parseCsv(MultipartFile file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                rows.add(line.split(",", -1));
            }
        }
        return rows;
    }

    private List<String[]> parseXlsx(MultipartFile file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            for (Row row : sheet) {
                int lastCol = row.getLastCellNum();
                if (lastCol <= 0) continue;
                String[] values = new String[lastCol];
                boolean anyValue = false;
                for (int c = 0; c < lastCol; c++) {
                    Cell cell = row.getCell(c);
                    values[c] = cell == null ? "" : formatter.formatCellValue(cell);
                    if (!values[c].isBlank()) anyValue = true;
                }
                if (anyValue) rows.add(values);
            }
        }
        return rows;
    }
}
