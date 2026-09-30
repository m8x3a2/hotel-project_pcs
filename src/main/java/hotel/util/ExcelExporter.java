package hotel.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Вспомогательный класс для экспорта табличных данных в Excel-файл (.xlsx).
 * Работает универсально: принимает заголовки столбцов и строки данных.
 */
public class ExcelExporter {

    static {
        // Apache POI пишет логи через Log4j2. Без этой настройки при первом экспорте в консоль
        // выводится "ERROR StatusLogger Log4j2 could not find a logging implementation".
        // Включаем встроенный простой логгер Log4j2, чтобы не подключать отдельную библиотеку.
        System.setProperty("log4j2.loggerContextFactory", "org.apache.logging.log4j.simple.SimpleLoggerContextFactory");
    }

    /**
     * Сохраняет переданные данные в .xlsx файл.
     *
     * @param filePath путь к файлу, который будет создан, например "export/bookings.xlsx"
     * @param sheetName название листа в Excel
     * @param headers  заголовки столбцов
     * @param rows     строки таблицы (каждая строка - массив строковых значений)
     */
    public static void export(String filePath, String sheetName, String[] headers, List<String[]> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName);

            // Стиль для заголовка таблицы (жирный шрифт)
            CellStyle headerStyle = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            headerStyle.setFont(boldFont);

            // Строка заголовков
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Строки с данными
            int rowIndex = 1;
            for (String[] rowData : rows) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < rowData.length; i++) {
                    row.createCell(i).setCellValue(rowData[i]);
                }
            }

            // Автоматически подгоняем ширину столбцов под содержимое
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream out = new FileOutputStream(filePath)) {
                workbook.write(out);
            }
        }
    }
}
