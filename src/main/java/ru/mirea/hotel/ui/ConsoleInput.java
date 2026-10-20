package ru.mirea.hotel.ui;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

/**
 * Чтение данных с клавиатуры.
 * Все методы повторяют вопрос, пока пользователь не введёт корректное значение,
 * поэтому программа не "падает", если вместо числа ввели текст.
 */
public class ConsoleInput {

    private final Scanner scanner = new Scanner(System.in, detectConsoleCharset());

    // ---------- Строки ----------

    public String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    /** Для изменения записи: показывает текущее значение, пустой ввод (Enter) - оставить как есть. */
    public String readLine(String prompt, String current) {
        String line = readLine(prompt + " [" + current + "]: ");
        return line.isEmpty() ? current : line;
    }

    // ---------- Целые числа ----------

    public int readInt(String prompt) {
        while (true) {
            String line = readLine(prompt);
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: нужно ввести целое число.");
            }
        }
    }

    public int readInt(String prompt, int current) {
        while (true) {
            String line = readLine(prompt + " [" + current + "]: ");
            if (line.isEmpty()) {
                return current;
            }
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: нужно ввести целое число.");
            }
        }
    }

    /** Целое число в диапазоне [min; max], например номер пункта меню. */
    public int readInt(String prompt, int min, int max) {
        while (true) {
            int value = readInt(prompt);
            if (value >= min && value <= max) {
                return value;
            }
            System.out.println("Ошибка: введите число от " + min + " до " + max + ".");
        }
    }

    // ---------- Дробные числа ----------

    public double readDouble(String prompt) {
        while (true) {
            String line = readLine(prompt).replace(',', '.'); // разрешаем писать и 2500,50
            try {
                return Double.parseDouble(line);
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: нужно ввести число, например 2500 или 2500.50");
            }
        }
    }

    public double readDouble(String prompt, double current) {
        while (true) {
            String line = readLine(prompt + " [" + current + "]: ").replace(',', '.');
            if (line.isEmpty()) {
                return current;
            }
            try {
                return Double.parseDouble(line);
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: нужно ввести число, например 2500 или 2500.50");
            }
        }
    }

    // ---------- Даты ----------

    public LocalDate readDate(String prompt) {
        while (true) {
            String line = readLine(prompt);
            try {
                return LocalDate.parse(line); // формат ГГГГ-ММ-ДД
            } catch (DateTimeParseException e) {
                System.out.println("Ошибка: дата должна быть в формате ГГГГ-ММ-ДД, например 2026-11-05.");
            }
        }
    }

    public LocalDate readDate(String prompt, LocalDate current) {
        while (true) {
            String line = readLine(prompt + " [" + current + "]: ");
            if (line.isEmpty()) {
                return current;
            }
            try {
                return LocalDate.parse(line);
            } catch (DateTimeParseException e) {
                System.out.println("Ошибка: дата должна быть в формате ГГГГ-ММ-ДД, например 2026-11-05.");
            }
        }
    }

    // ---------- Выбор из списка и подтверждение ----------

    /**
     * Показывает пронумерованный список вариантов и возвращает выбранный.
     * <T> - generic: метод работает с массивом любого типа (RoomType[], BookingStatus[] ...).
     */
    public <T> T choose(String title, T[] options) {
        System.out.println(title);
        for (int i = 0; i < options.length; i++) {
            System.out.println("  " + (i + 1) + ". " + options[i]);
        }
        int index = readInt("Ваш выбор: ", 1, options.length);
        return options[index - 1];
    }

    /** Вопрос "да/нет" (например, перед удалением). */
    public boolean confirm(String question) {
        String answer = readLine(question + " (да/нет): ").toLowerCase();
        return answer.equals("да") || answer.equals("д") || answer.equals("y") || answer.equals("yes");
    }

    /**
     * Определяем кодировку консоли. В Windows консоль часто работает в cp866, а не в UTF-8,
     * и без этого русские буквы при вводе превращаются в "кракозябры".
     * При проблемах можно задать кодировку вручную: -Dconsole.encoding=UTF-8
     */
    private static Charset detectConsoleCharset() {
        String manual = System.getProperty("console.encoding");
        if (manual != null && Charset.isSupported(manual)) {
            return Charset.forName(manual);
        }
        if (System.console() != null) {   // программа запущена в настоящем терминале (cmd, PowerShell)
            return System.console().charset();
        }
        String stdout = System.getProperty("stdout.encoding"); // например, консоль IntelliJ IDEA (UTF-8)
        if (stdout != null && Charset.isSupported(stdout)) {
            return Charset.forName(stdout);
        }
        return Charset.defaultCharset();
    }
}
