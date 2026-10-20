package ru.mirea.hotel.util;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import ru.mirea.hotel.model.Booking;
import ru.mirea.hotel.model.Guest;
import ru.mirea.hotel.model.Room;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Экспорт данных в Excel (.xlsx) с помощью библиотеки Apache POI.
 * В файле три листа: "Гости", "Номера", "Бронирования".
 * Устройство Excel в POI: Workbook (файл) -> Sheet (лист) -> Row (строка) -> Cell (ячейка).
 */
public class ExcelExporter {

    public static void export(String fileName, List<Guest> guests, List<Room> rooms, List<Booking> bookings)
            throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream out = new FileOutputStream(fileName)) {

            CellStyle headerStyle = createHeaderStyle(workbook);

            // ---- Лист 1: Гости ----
            Sheet guestSheet = workbook.createSheet("Гости");
            createHeader(guestSheet, headerStyle, "ID", "ФИО", "Телефон", "Email");
            int rowIndex = 1; // строка 0 - заголовок
            for (Guest guest : guests) {
                Row row = guestSheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(guest.getId());
                row.createCell(1).setCellValue(guest.getFullName());
                row.createCell(2).setCellValue(guest.getPhone());
                row.createCell(3).setCellValue(guest.getEmail());
            }
            autoSizeColumns(guestSheet, 4);

            // ---- Лист 2: Номера ----
            Sheet roomSheet = workbook.createSheet("Номера");
            createHeader(roomSheet, headerStyle, "ID", "Номер", "Тип", "Мест", "Цена за ночь, руб.");
            rowIndex = 1;
            for (Room room : rooms) {
                Row row = roomSheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(room.getId());
                row.createCell(1).setCellValue(room.getRoomNumber());
                row.createCell(2).setCellValue(room.getType().getTitle());
                row.createCell(3).setCellValue(room.getCapacity());
                row.createCell(4).setCellValue(room.getPricePerNight());
            }
            autoSizeColumns(roomSheet, 5);

            // ---- Лист 3: Бронирования ----
            Sheet bookingSheet = workbook.createSheet("Бронирования");
            createHeader(bookingSheet, headerStyle, "ID", "Гость", "Номер", "Тип номера", "Заезд", "Выезд",
                    "Ночей", "Гостей", "Статус", "Стоимость, руб.");
            rowIndex = 1;
            for (Booking booking : bookings) {
                Row row = bookingSheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(booking.getId());
                row.createCell(1).setCellValue(booking.getGuestName());
                row.createCell(2).setCellValue(booking.getRoomNumber());
                row.createCell(3).setCellValue(booking.getRoomType().getTitle());
                row.createCell(4).setCellValue(booking.getCheckIn().toString());
                row.createCell(5).setCellValue(booking.getCheckOut().toString());
                row.createCell(6).setCellValue(booking.getNights());
                row.createCell(7).setCellValue(booking.getGuestsCount());
                row.createCell(8).setCellValue(booking.getStatus().getTitle());
                row.createCell(9).setCellValue(booking.getTotalPrice());
            }
            autoSizeColumns(bookingSheet, 10);

            workbook.write(out); // записываем всё в файл
        }
    }

    /** Жирный шрифт для строки заголовков. */
    private static CellStyle createHeaderStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    /** Создаёт первую строку листа с названиями колонок. String... - любое количество строк. */
    private static void createHeader(Sheet sheet, CellStyle style, String... titles) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < titles.length; i++) {
            header.createCell(i).setCellValue(titles[i]);
            header.getCell(i).setCellStyle(style);
        }
    }

    /** Подгоняет ширину колонок под содержимое. */
    private static void autoSizeColumns(Sheet sheet, int columnsCount) {
        for (int i = 0; i < columnsCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
