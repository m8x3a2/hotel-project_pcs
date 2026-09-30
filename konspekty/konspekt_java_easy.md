# Java «по-простому» на примере нашей гостиницы

Это подробная версия [KONSPEKT_JAVA.md](KONSPEKT_JAVA.md). Здесь каждая тема разобрана
медленно, с аналогиями и пошаговыми примерами на **нашем** коде и **наших** тестовых данных
из `sql/init.sql`.

Совет: читайте с открытым кодом проекта рядом. Когда в тексте написано «см. `UserService`»,
откройте этот файл и найдите место глазами.

---

## Содержание

0. [Как устроена память: стек, куча, ссылки](#0-как-устроена-память-стек-куча-ссылки) — **основа для всего остального**
1. [Путь одного действия: от нажатия клавиши до строки в базе](#1-путь-одного-действия-от-нажатия-клавиши-до-строки-в-базе)
2. [ООП: инкапсуляция, наследование, полиморфизм, абстракция](#2-ооп-инкапсуляция-наследование-полиморфизм-абстракция)
3. [equals, hashCode, toString — подробно](#3-equals-hashcode-tostring--подробно)
4. [Интерфейсы и generics](#4-интерфейсы-и-generics)
5. [enum](#5-enum)
6. [Исключения: как ошибка «летит» через программу](#6-исключения-как-ошибка-летит-через-программу)
7. [Коллекции: List, Map, Set и Optional](#7-коллекции-list-map-set-и-optional)
8. [Лямбды: от длинного кода к короткому](#8-лямбды-от-длинного-кода-к-короткому)
9. [Stream API по шагам на наших данных](#9-stream-api-по-шагам-на-наших-данных)
10. [Даты](#10-даты)
11. [Регулярные выражения: читаем по символу](#11-регулярные-выражения-читаем-по-символу)
12. [static и final](#12-static-и-final)
13. [switch со стрелками](#13-switch-со-стрелками)
14. [Scanner и надёжный ввод](#14-scanner-и-надёжный-ввод)
15. [JDBC: разговор Java с базой данных](#15-jdbc-разговор-java-с-базой-данных)
16. [Maven](#16-maven)
17. [Проверь себя](#17-проверь-себя)

---

## 0. Как устроена память: стек, куча, ссылки

Без этого раздела тема `equals` (раздел 3) не будет понятна. Он короткий.

В Java есть два вида переменных:

**1. Примитивы** — `int`, `double`, `boolean`, `char`, `long` и ещё несколько.
Переменная хранит **само значение**.

```java
int roomNumber = 101;
```
Представьте коробку с надписью `roomNumber`, внутри лежит число `101`.

**2. Ссылочные типы** — всё остальное: `String`, `User`, `Booking`, `List`, массивы...
Переменная хранит **не объект, а адрес объекта** (ссылку). Сам объект лежит в отдельной
области памяти — **куче** (heap).

```java
User user = new User("Иванов Иван", "+7 900 111-11-11", "ivanov@mail.ru", "4500111111");
```

Что происходит:
1. `new User(...)` создаёт объект в куче. Допустим, по адресу `#A1`.
2. В переменную `user` кладётся **адрес** `#A1`.

```
  Переменные (стек)          Объекты (куча)
  ┌────────────────┐         ┌──────────────────────────────┐
  │ user  =  #A1 ──┼───────► │ #A1: User                    │
  └────────────────┘         │   id = 0                     │
                             │   fullName = "Иванов Иван"   │
                             │   ...                        │
                             └──────────────────────────────┘
```

Аналогия: объект — это **дом**, а переменная — **бумажка с адресом дома**.

### Что будет при копировании?

```java
User a = new User("Иванов Иван", ...);   // a = #A1
User b = a;                               // b = #A1 — скопировали БУМАЖКУ, а не дом!
b.setFullName("Петров Пётр");
System.out.println(a.getFullName());      // "Петров Пётр"  ← изменился и через a!
```

Бумажек две, а дом один. Покрасили дом по адресу с бумажки `b`, пришли по адресу
с бумажки `a` — а это тот же дом, он тоже покрашен.

### А если создать два объекта с одинаковыми данными?

```java
User a = new User("Иванов Иван", ...);   // #A1
User c = new User("Иванов Иван", ...);   // #B7 — новый, отдельный дом
```

Два **разных** дома, хоть и одинаково покрашенных. Это ключ к разделу 3.

### Где это есть в нашем коде

В `UserService.updateUser`:
```java
User user = getUserById(id);      // получили ссылку на объект
user.setFullName(newFullName);    // меняем объект по этой ссылке
userRepository.update(user);      // передаём ту же ссылку в репозиторий
```
Репозиторий получает **тот же самый** объект с уже изменённым именем. Копия не создаётся.

### `null`

`null` — «бумажка пустая, адреса нет». Если вызвать метод у `null`
(`String s = null; s.trim();`), получится `NullPointerException`: нельзя пойти в дом,
адреса которого нет.

---

## 1. Путь одного действия: от нажатия клавиши до строки в базе

Проследим, что происходит, когда пользователь добавляет гостя. Так видно, зачем нужны
все слои.

```
Пользователь         Main                 Validator         UserService          UserRepository        PostgreSQL
    │  "1" (Гости)     │                        │                 │                      │                    │
    │  "1" (Добавить)  │                        │                 │                      │                    │
    │─────────────────►│ createGuest()          │                 │                      │                    │
    │  "ivanov@MAIL.ru"│ readValidString(...) ─►│ validateEmail   │                      │                    │
    │                  │◄── "ivanov@mail.ru" ───│                 │                      │                    │
    │  (ещё 3 поля)    │ ...                    │                 │                      │                    │
    │                  │ createUser(...) ──────────────────────────►│                      │                    │
    │                  │                        │◄─ проверка ещё раз─│                      │                    │
    │                  │                        │                 │ checkUniqueness ────►│ findAll() ────────►│ SELECT
    │                  │                        │                 │ save(user) ─────────►│ INSERT ...─────────►│
    │                  │                        │                 │                      │◄── id = 6 ─────────│
    │                  │◄──────────────── User #6 ─────────────────│◄─────────────────────│                    │
    │◄─ "Гость успешно добавлен: Гость #6 ..."  │                 │                      │                    │
```

Словами:
1. **`Main.guestsMenu`** читает `"1"` и через `switch` вызывает `createGuest()`.
2. **`createGuest`** четыре раза вызывает `readValidString`, по разу на каждое поле.
   Каждое поле сразу проверяется через `Validator`. Если ошибка, поле переспрашивается.
3. Готовые значения передаются в **`userService.createUser(...)`**.
4. **Сервис** проверяет поля ещё раз (вдруг его вызвали не из меню) и проверяет уникальность
   email и паспорта.
5. **Сервис** создаёт объект `User` (пока с `id = 0`) и отдаёт его в `userRepository.save(user)`.
6. **Репозиторий** выполняет SQL `INSERT`, получает от базы новый `id` и записывает его
   в объект: `user.setId(6)`.
7. Объект возвращается по цепочке обратно в `Main`, и тот печатает его через `toString()`.

**Главная мысль:** `Main` не знает про SQL, репозиторий не знает про правила, сервис не
знает про консоль. Каждый делает одно дело.

---

## 2. ООП: инкапсуляция, наследование, полиморфизм, абстракция

### 2.1 Инкапсуляция — «не лезь внутрь, пользуйся кнопками»

Аналогия: у банкомата есть кнопки, но нельзя открыть его и взять деньги руками.

```java
public class User {
    private String email;                              // спрятано: доступно только внутри User

    public String getEmail() { return email; }         // «кнопка» прочитать
    public void setEmail(String email) { this.email = email; }  // «кнопка» записать
}
```

Если бы поле было `public`:
```java
user.email = null;   // кто угодно, где угодно, без контроля
```

С `private` в `Main` так написать **нельзя**: компилятор выдаст ошибку. Нужно вызвать
`user.setEmail(...)`. Плюс: в сеттер можно добавить проверку, и она сработает везде
одновременно.

> **Зачем тогда сеттер, если он просто присваивает?** Сейчас он простой, но точка контроля
> уже есть. Если завтра нужно логировать изменения email, правим один метод, а не сто мест
> в программе.

### 2.2 Наследование — «взять готовое и дополнить»

```java
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
```

Разберём по словам:
- `extends RuntimeException` — «`BusinessException` — это разновидность `RuntimeException`».
  Наш класс **получает всё**, что умеет родитель: метод `getMessage()`, умение
  «бросаться» через `throw`, печать стека вызовов и т.д. Мы это не писали, оно досталось
  по наследству.
- `super(message)` — «родитель, сохрани у себя это сообщение». В `RuntimeException` уже
  есть поле для сообщения, мы просто передаём ему текст.

Поэтому в `Main` работает:
```java
catch (BusinessException e) {
    System.out.println("Ошибка: " + e.getMessage());   // getMessage() написан не нами — унаследован
}
```

Зачем свой класс, а не просто `RuntimeException`? Чтобы в `catch` отличить **наши**
ожидаемые ошибки («неверный email») от **настоящих багов** (`NullPointerException`).
Наши показываем пользователю, баги — нет.

Цепочка наследования:
```
Object                  ← корень ВСЕХ классов Java (даже если extends не написан)
 └─ Throwable
     └─ Exception
         └─ RuntimeException
             └─ BusinessException    ← наш
```

Раз всё наследуется от `Object`, у **любого** объекта есть методы `equals`, `hashCode`,
`toString`. Про них — раздел 3.

### 2.3 Полиморфизм — «одна команда, разное выполнение»

Аналогия: команда «Открыть!». Дверь открывается поворотом ручки, окно — поворотом рамы,
банка — ключом. Команда одна, способ у каждого свой.

У нас: интерфейс `CrudRepository` говорит «у репозитория есть `save`». Но:
- `UserRepository.save` делает `INSERT INTO users (full_name, phone, ...)`;
- `BookingRepository.save` делает `INSERT INTO bookings (user_id, room_number, ...)`.

```java
CrudRepository<User> r1 = new UserRepository();
CrudRepository<Booking> r2 = new BookingRepository();

r1.findAll();   // выполнится SELECT * FROM users
r2.findAll();   // выполнится SELECT * FROM bookings
```

Тип переменной одинаковый по сути (`CrudRepository`), а Java **во время работы** смотрит,
какой объект на самом деле лежит по ссылке, и вызывает **его** версию метода.

Другой пример, который работает постоянно:
```java
System.out.println(user);     // вызовется User.toString()    → "Гость #1 | ФИО: ..."
System.out.println(booking);  // вызовется Booking.toString() → "Бронирование #1 | ..."
```
`println` про наши классы ничего не знает. Он просто вызывает `toString()`, а какой именно,
решает объект. Это и есть полиморфизм.

### 2.4 Абстракция — «знать ЧТО, а не КАК»

Аналогия: вы водите машину педалью газа и не думаете о поршнях.

Сервис пишет `userRepository.findAll()` и **не знает**, что внутри JDBC, SQL, соединения.
Он знает только «дай всех гостей». Если завтра гостей будут хранить в файле, сервис
не изменится.

---

## 3. equals, hashCode, toString — подробно

Это самая «непонятная» тема, разберём по шагам.

### 3.1 Проблема: как понять, что два объекта «одинаковые»?

Вспомним раздел 0:

```java
User a = new User(1, "Иванов Иван", "+7 900 111-11-11", "ivanov@mail.ru", "4500111111");
User b = new User(1, "Иванов Иван", "+7 900 111-11-11", "ivanov@mail.ru", "4500111111");
```

Это два **разных дома** (`#A1` и `#B7`) с одинаковыми данными. Одинаковые ли они?

Смотря что спрашиваем:
- **«Это один и тот же объект в памяти?»** — нет. Это вопрос `==`.
- **«Это один и тот же гость?»** — по смыслу да: у обоих `id = 1`. Это вопрос `equals`.

### 3.2 Оператор `==`

Для ссылочных типов `==` сравнивает **адреса** (бумажки), а не содержимое.

```java
a == b    // false: #A1 != #B7, разные объекты
a == a    // true: адрес тот же
```

Для примитивов `==` сравнивает значения, там всё просто: `101 == 101` → `true`.

### 3.3 Метод `equals` — «одинаковые ли по смыслу?»

Что считать «одинаковым», решает **программист**. Мы решили: гости одинаковые, если
совпадает `id` (это же первичный ключ в базе).

Разберём наш `User.equals` построчно:

```java
@Override
public boolean equals(Object o) {
```
- `@Override` — «переопределяю метод, который уже есть у `Object`».
- Параметр типа `Object`, а не `User`: так метод объявлен в `Object`, сигнатура должна
  совпадать. Сравнить можно с чем угодно: со строкой, числом, бронированием.

```java
    if (this == o) return true;
```
- «Если мне передали меня же (тот же адрес), то конечно равны». Быстрая проверка:
  дальше можно не смотреть.

```java
    if (!(o instanceof User)) return false;
```
- `instanceof` проверяет: «является ли `o` объектом класса `User`?»
- Если передали `Booking` или строку, то это точно не тот же гость → `false`.
- Если `o == null`, то `instanceof` тоже даёт `false`. Так мы заодно защищены от `null`.

```java
    User user = (User) o;
```
- **Приведение типа.** Переменная `o` имеет тип `Object`, и у неё Java «не видит» поле `id`.
  Мы уже проверили, что там на самом деле `User`, и говорим Java: «считай это `User`».
  Теперь доступно `user.id`.

```java
    return id == user.id;
}
```
- Сравниваем `id` (это `int`, примитив, поэтому `==` сравнивает значения).
- Поле `user.id` — `private`, но мы внутри класса `User`, поэтому доступ есть.

Итог:
```java
a == b          // false — разные объекты
a.equals(b)     // true  — один и тот же гость (id = 1)
```

### 3.4 Что было бы без нашего `equals`?

Если метод не переопределить, работает версия из `Object`, а она устроена так:
```java
public boolean equals(Object o) { return this == o; }   // просто сравнение адресов
```
То есть `a.equals(b)` было бы `false`. Одного и того же гостя, дважды прочитанного из базы,
Java считала бы разными людьми.

### 3.5 Пример, где это важно

Каждый вызов `userRepository.findById(1)` создаёт **новый** объект (в `mapRow` стоит
`new User(...)`). Значит:

```java
User first  = userService.getUserById(1);   // новый объект #A1
User second = userService.getUserById(1);   // ещё один новый объект #C3

first == second        // false! Хотя это один и тот же Иванов
first.equals(second)   // true  — благодаря нашему equals
```

Или со списком:
```java
List<User> vip = new ArrayList<>();
vip.add(userService.getUserById(1));

vip.contains(userService.getUserById(1));   // true — contains внутри вызывает equals
```
Без нашего `equals` получилось бы `false`: «Иванова в списке нет», хотя он там есть.

### 3.6 Зачем тогда `hashCode`?

Аналогия: **гардероб с номерками** (или камера хранения с ячейками).

`HashMap` и `HashSet` хранят объекты не в одну длинную очередь (тогда поиск шёл бы
по всем подряд), а **раскладывают по ячейкам**. Номер ячейки вычисляется из объекта,
это и есть `hashCode()`: метод, который превращает объект в число.

**Как `HashSet` ищет объект:**
1. Вычисляет `hashCode()` искомого объекта → получает номер ячейки, например 17.
2. Идёт **только в ячейку 17**.
3. В ячейке могут лежать несколько объектов, и каждый сравнивается через `equals`.

Это очень быстро: не нужно перебирать тысячи объектов, только ячейку.

**Что сломается, если переопределить `equals`, но забыть `hashCode`:**

`hashCode()` из `Object` по умолчанию разный у разных объектов (грубо говоря, зависит
от адреса). Тогда:

```java
Set<User> guests = new HashSet<>();
guests.add(userService.getUserById(1));       // объект #A1 → hashCode, скажем, 842 → ячейка 842

guests.contains(userService.getUserById(1));  // объект #C3 → hashCode 519 → ищем в ячейке 519
                                              // там пусто → false!
```

До нашего `equals` дело даже не дошло: Java искала **не в той ячейке**. Отсюда правило:

> **Контракт:** если `a.equals(b) == true`, то обязательно `a.hashCode() == b.hashCode()`.
> Переопределил `equals` — переопредели и `hashCode` по **тем же полям**.

Наш `hashCode`:
```java
@Override
public int hashCode() {
    return Objects.hash(id);
}
```
`Objects.hash(id)` вычисляет число только по `id`. У двух объектов с `id = 1` всегда
получится одно и то же число, значит, одна ячейка. Контракт выполнен.

Обратное **не обязательно**: у разных объектов `hashCode` может случайно совпасть
(«коллизия»). Не страшно: в одной ячейке лежат несколько объектов, и их различит `equals`.

### 3.7 Где `hashCode` реально работает в нашем проекте

В `BookingService.searchByGuestName`:
```java
Map<Integer, User> usersById = userRepository.findAll().stream()
        .collect(Collectors.toMap(User::getId, u -> u));
```
Строится `HashMap`: ключ — `id` гостя (`Integer`), значение — сам гость.
Потом для каждого бронирования:
```java
User u = usersById.get(b.getUserId());
```
`get` вычисляет `hashCode` ключа, идёт в нужную ячейку и мгновенно находит гостя, без
перебора всех гостей. Здесь используются `hashCode`/`equals` класса `Integer`: их за нас
уже правильно написали создатели Java.

### 3.8 Тонкость нашего `equals` (хорошо знать для защиты)

У **нового** гостя, ещё не сохранённого в базу, `id = 0`. Значит, два разных новых гостя
будут «равны» по `equals`:
```java
new User("Иванов Иван", ...).equals(new User("Петров Пётр", ...))   // true (оба id = 0)
```
В нашей программе это не мешает: новых гостей мы сразу сохраняем и не складываем в
коллекции. Если спросят, можно ответить: «сравнение по `id` корректно для объектов из базы;
для несохранённых объектов пришлось бы сравнивать по другим полям, например по email».

### 3.9 `toString` — «как объект выглядит текстом»

Когда Java нужно превратить объект в строку (`println(user)` или `"Гость: " + user`),
она вызывает `toString()`.

Без переопределения работает версия из `Object`, и выводится что-то вроде:
```
hotel.model.User@1b6d3586
```
(имя класса и число из `hashCode` в шестнадцатеричной записи — бесполезно для человека).

Наш вариант:
```java
@Override
public String toString() {
    return "Гость #" + id +
            " | ФИО: " + fullName +
            " | Телефон: " + phone +
            " | Email: " + email +
            " | Паспорт: " + passportNumber;
}
```
Вывод:
```
Гость #1 | ФИО: Иванов Иван Иванович | Телефон: +7 900 111-11-11 | Email: ivanov@mail.ru | Паспорт: 4500111111
```

Где используется **неявно**:
```java
System.out.println("Гость успешно добавлен: " + user);   // Main.createGuest
users.forEach(System.out::println);                       // Main.printUsers
```
Ни там, ни там `toString()` явно не написан, Java вызывает его сама.

### 3.10 Шпаргалка по разделу 3

| Вопрос | Ответ |
|---|---|
| Что сравнивает `==` у объектов? | адреса: тот же ли это объект |
| Что сравнивает `equals`? | то, что решил программист; у нас `id` |
| Что делает `equals` из `Object`? | то же, что `==` |
| Зачем `hashCode`? | номер «ячейки» в `HashMap`/`HashSet` для быстрого поиска |
| Правило? | равны по `equals` → обязаны иметь одинаковый `hashCode` |
| Что будет, если забыть `hashCode`? | `HashSet.contains`/`HashMap.get` не найдут «равный» объект |
| Когда вызывается `toString`? | при печати и склейке со строкой, автоматически |
| Строки сравнивать через? | `equals`, не `==` |

Про строки: `String` — тоже объект. `"abc" == "abc"` иногда даёт `true` (Java
переиспользует одинаковые строковые литералы), но строка, введённая с клавиатуры, — всегда
новый объект. Поэтому `input == "1"` будет `false`, даже если ввели `1`. Всегда пишите
`input.equals("1")`. В нашем `switch (choice)` Java сама сравнивает строки через `equals`.

---

## 4. Интерфейсы и generics

### 4.1 Интерфейс — договор

Аналогия: **должностная инструкция**. В ней написано «кассир обязан: принимать оплату,
выдавать чек». Как именно, решает конкретный кассир.

```java
public interface CrudRepository<T> {
    T save(T entity) throws SQLException;
    List<T> findAll() throws SQLException;
    Optional<T> findById(int id) throws SQLException;
    void update(T entity) throws SQLException;
    void delete(int id) throws SQLException;
}
```
У методов нет тела `{ ... }`, только «заголовки». Это список обязанностей.

```java
public class UserRepository implements CrudRepository<User> { ... }
```
`implements` = «подписываю договор». Теперь компилятор **заставит** написать все 5 методов.
Забыли `delete`? Ошибка компиляции. Так интерфейс гарантирует, что оба репозитория умеют
одно и то же.

### 4.2 Generics — «шаблон с пропуском для типа»

Что за `<T>`? Это **пропуск**, который заполняется конкретным типом.

Аналогия: бланк «Заявление о ____». Один бланк, но заполнить можно «о приёме», «об отпуске».

```java
interface CrudRepository<T> {
    T save(T entity);
    List<T> findAll();
}
```

Когда пишем `implements CrudRepository<User>`, Java мысленно подставляет `User` вместо `T`:
```java
User save(User entity);
List<User> findAll();
```
А для `CrudRepository<Booking>` подставляется `Booking`.

**Что было бы без generics?** Пришлось бы писать
```java
List findAll();   // список «чего-то»
```
и потом вручную приводить: `User u = (User) list.get(0);`. Если в списке оказался `Booking`,
программа упала бы во время работы. С generics ошибка видна **при компиляции**:
```java
List<User> users = userRepository.findAll();
users.add(new Booking(...));   // ОШИБКА КОМПИЛЯЦИИ: в список гостей нельзя положить бронь
```

### 4.3 Почему `List<Integer>`, а не `List<int>`?

Generics работают только с объектами, а `int` — примитив. Для каждого примитива есть
класс-«обёртка»: `int` → `Integer`, `double` → `Double`, `boolean` → `Boolean`.
Java переводит между ними автоматически (**автоупаковка**):
```java
Map<Integer, User> usersById = ...;
usersById.get(b.getUserId());   // getUserId() возвращает int, Java сама превращает в Integer
```

---

## 5. enum

### 5.1 Зачем

Статус брони мог бы быть строкой:
```java
String status = "CONFIRMED";
if (status.equals("CONFIRMD")) ...   // опечатка! Компилятор молчит, условие просто всегда false
```
С enum:
```java
BookingStatus status = BookingStatus.CONFIRMED;
if (status == BookingStatus.CONFIRMD) ...   // ОШИБКА КОМПИЛЯЦИИ: такого значения нет
```

enum — это **закрытый список** допустимых значений. Других не бывает.

### 5.2 enum — это полноценный класс

`RoomType` хранит цену:
```java
public enum RoomType {
    STANDARD(2000.0),
    DELUXE(3500.0),
    SUITE(5000.0),
    FAMILY(4500.0);

    private final double pricePerNight;

    RoomType(double pricePerNight) {
        this.pricePerNight = pricePerNight;
    }

    public double getPricePerNight() { return pricePerNight; }
}
```

Как это читать:
- `STANDARD(2000.0)` — «создай объект `STANDARD`, вызвав конструктор с числом 2000.0».
  Java создаёт ровно 4 объекта при запуске программы, и новых не появится.
- Поэтому enum можно сравнивать через `==`: каждый `SUITE` — один и тот же объект.

Использование в расчёте цены (`BookingService.calculatePrice`):
```java
long nights = ChronoUnit.DAYS.between(checkIn, checkOut);   // 3 ночи
return nights * roomType.getPricePerNight();                // 3 * 3500.0 = 10500.0 для DELUXE
```
Цена «приклеена» к типу номера. Не нужен `if (type == DELUXE) price = 3500; else if ...`.

### 5.3 Как enum попадает в базу и обратно

В базе нет типа «RoomType», там `VARCHAR`. Поэтому:
```java
statement.setString(3, booking.getRoomType().name());        // DELUXE → "DELUXE" (запись)
RoomType.valueOf(resultSet.getString("room_type"));          // "DELUXE" → DELUXE (чтение)
```

### 5.4 Меню из enum

`Main.readRoomType`:
```java
RoomType[] values = RoomType.values();     // [STANDARD, DELUXE, SUITE, FAMILY]
for (int i = 0; i < values.length; i++) {
    System.out.println((i + 1) + ". " + values[i] + " (" + values[i].getPricePerNight() + " руб/ночь)");
}
```
Добавите в enum `PRESIDENT(15000.0)`, и он сам появится в меню. Код меню менять не нужно.

### 5.5 Переходы статусов

```java
Map.of(
    BookingStatus.CREATED,   EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED),
    BookingStatus.CONFIRMED, EnumSet.of(BookingStatus.CHECKED_IN, BookingStatus.CANCELLED),
    ...
)
```
Читается как таблица «откуда → куда можно»:

| Сейчас | Можно перейти в |
|---|---|
| CREATED | CONFIRMED, CANCELLED |
| CONFIRMED | CHECKED_IN, CANCELLED |
| CHECKED_IN | CHECKED_OUT |
| CHECKED_OUT | — (конец) |
| CANCELLED | — (конец) |

Пример: бронь #2 в статусе `CONFIRMED`, пытаемся перевести в `CHECKED_OUT`:
```java
Set<BookingStatus> allowed = ALLOWED_TRANSITIONS.get(CONFIRMED);   // {CHECKED_IN, CANCELLED}
allowed.contains(CHECKED_OUT)                                       // false
→ throw new BusinessException("Нельзя перевести бронирование из статуса CONFIRMED в статус CHECKED_OUT.");
```

---

## 6. Исключения: как ошибка «летит» через программу

### 6.1 Идея

Когда метод обнаружил проблему, он может не возвращать «код ошибки», а **бросить
исключение**. Исключение «летит» вверх по цепочке вызовов, пока его кто-то не поймает.

Аналогия: на кухне ресторана кончились продукты. Повар не решает сам, что сказать гостю,
он сообщает официанту, а официант объясняет гостю.

### 6.2 Трассировка на нашем коде

Пользователь вводит email `ivanov@mail.ru`, который уже занят (гость #1).

```
main()
 └─ mainMenu()                  ← здесь стоит try/catch
     └─ guestsMenu()
         └─ createGuest()
             └─ userService.createUser(...)
                 └─ checkUniqueness(...)
                     └─ throw new BusinessException("Гость с таким email уже существует: ivanov@mail.ru")
```

Что происходит по шагам:
1. В `checkUniqueness` выполняется `throw`. Метод **мгновенно прерывается**, следующие
   строки не выполняются.
2. В `createUser` нет `try/catch`, поэтому он тоже прерывается. Строка
   `userRepository.save(user)` **не выполнится**: гость не сохранится. Это и нужно.
3. В `createGuest` строка `System.out.println("Гость успешно добавлен...")` тоже
   **не выполнится**.
4. `guestsMenu` прерывается.
5. В `mainMenu` есть `try/catch`, исключение поймано:
   ```java
   } catch (EntityNotFoundException | BusinessException e) {
       System.out.println("Ошибка: " + e.getMessage());
   }
   ```
   Печатается «Ошибка: Гость с таким email уже существует: ivanov@mail.ru».
6. Цикл `while (running)` продолжается, и снова показывается меню. **Программа не упала.**

Если бы `try/catch` не было нигде, исключение долетело бы до `main`, и программа
завершилась бы с красным текстом ошибки.

### 6.3 Проверяемые и непроверяемые

**Проверяемые (checked)** — наследники `Exception`, но не `RuntimeException`.
Пример: `SQLException`. Компилятор **требует** выбрать одно из двух:
- поймать: `try { ... } catch (SQLException e) { ... }`
- или честно предупредить в сигнатуре: `throws SQLException` — «я могу это бросить,
  пусть разбирается вызывающий».

Поэтому почти у всех методов репозиториев и сервисов написано `throws SQLException`:
ошибку базы «передают наверх» до `mainMenu`, где её ловят.

**Непроверяемые (unchecked)** — наследники `RuntimeException`. Компилятор ничего
не требует. Наши `BusinessException` и `EntityNotFoundException` такие, иначе пришлось бы
дописывать `throws BusinessException` к десяткам методов.

### 6.4 try-with-resources — «уходя, гасите свет»

Соединение с базой — дорогой ресурс, его обязательно нужно закрыть. Старый способ:
```java
Connection c = null;
try {
    c = DatabaseManager.getConnection();
    ... работа ...
} finally {
    if (c != null) c.close();    // выполнится всегда, даже при ошибке
}
```
Современный (у нас):
```java
try (Connection connection = DatabaseManager.getConnection();
     PreparedStatement statement = connection.prepareStatement(sql)) {
    ... работа ...
}   // ← здесь Java сама вызовет statement.close(), потом connection.close()
```
Всё, что объявлено в круглых скобках после `try`, закроется **автоматически**, даже если
внутри вылетело исключение. Работает для классов, реализующих интерфейс `AutoCloseable`.

### 6.5 Перевод «технической» ошибки в «человеческую»

`UserService.deleteUser`: пытаемся удалить Иванова (#1), у которого есть брони.
База отвечает ошибкой внешнего ключа с кодом `23503`.
```java
try {
    userRepository.delete(id);
} catch (SQLException e) {
    if ("23503".equals(e.getSQLState())) {
        throw new BusinessException("Нельзя удалить гостя: у него есть бронирования. ...");
    }
    throw e;   // другая ошибка (например, база выключена) — не маскируем её, пусть летит дальше
}
```
Пользователь видит понятный текст, а не `ERROR: update or delete on table "users" violates
foreign key constraint...`.

---

## 7. Коллекции: List, Map, Set и Optional

### 7.1 List — список по порядку

Как очередь или нумерованный список. Порядок сохраняется, дубликаты разрешены,
элементы доступны по номеру (с нуля).

```java
List<User> users = new ArrayList<>();
users.add(user);               // добавить в конец
users.get(0);                  // первый элемент
users.size();                  // сколько
users.isEmpty();               // пуст ли
for (User u : users) { ... }   // перебрать все
```

В `UserRepository.findAll`:
```java
List<User> users = new ArrayList<>();
while (resultSet.next()) {           // пока в результате SQL есть строки
    users.add(mapRow(resultSet));    // превращаем строку в User и добавляем
}
return users;
```

`List` — интерфейс («договор»), `ArrayList` — конкретная реализация (внутри массив,
который сам расширяется). Переменную объявляют типом интерфейса: `List<User> users = new ArrayList<>()`.
Так проще потом заменить реализацию.

### 7.2 Map — словарь «ключ → значение»

Как телефонная книга: по имени (ключу) находим номер (значение). Ключи не повторяются.

```java
Map<String, String> stats = new LinkedHashMap<>();
stats.put("Всего гостей", "5");
stats.put("Всего бронирований", "12");
stats.get("Всего гостей");         // "5"
```

Перебор в `Main.showStatistics`:
```java
for (Map.Entry<String, String> entry : stats.entrySet()) {
    System.out.println(entry.getKey() + ": " + entry.getValue());
}
```
`Entry` — одна пара «ключ + значение».

**Почему `LinkedHashMap`, а не `HashMap`?** `HashMap` раскладывает по «ячейкам» (раздел 3.6),
и при переборе порядок получается непредсказуемый. Статистика вывелась бы вперемешку.
`LinkedHashMap` дополнительно помнит **порядок добавления**.

### 7.3 Set — множество без повторов

`EnumSet.of(CONFIRMED, CANCELLED)` — набор разрешённых статусов. Главная операция:
`contains` («есть ли в наборе?»).

### 7.4 Optional — «коробка, которая может быть пустой»

Проблема: `findById(999)` — гостя нет. Что вернуть? Можно `null`, но вызывающий может
забыть проверить, и будет `NullPointerException`.

Решение — вернуть **коробку**:
```java
Optional.of(user)     // коробка с гостем
Optional.empty()      // пустая коробка
```

Тот, кто получил коробку, **вынужден** подумать, что делать, если она пустая:
```java
// UserService.getUserById
return userRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Гость с ID " + id + " не найден."));
```
Читается: «достань гостя из коробки, а если коробка пустая, брось исключение».

```java
// BookingService.createBooking
if (userRepository.findById(userId).isEmpty()) { ... }   // «коробка пустая?»
```

---

## 8. Лямбды: от длинного кода к короткому

Лямбды пугают, пока не увидишь, **откуда** они берутся. Пройдём путь за три шага.

### Шаг 1. Задача

В `Main` нужно много похожих методов ввода: читать email, пока не будет правильным;
читать телефон, пока не будет правильным... Отличается только **проверка**.
Хочется написать один метод и передать ему проверку как параметр.

Но в Java параметр — это значение или объект. Как передать **действие**?

### Шаг 2. Через интерфейс (старый способ)

Есть стандартный интерфейс с одним методом:
```java
interface UnaryOperator<T> {
    T apply(T t);      // взять T, вернуть T
}
```
Наш метод принимает любой объект с таким методом:
```java
private static String readValidString(String prompt, UnaryOperator<String> validator) {
    while (true) {
        String input = readNonEmptyString(prompt);
        try {
            return validator.apply(input);     // вызываем переданную проверку
        } catch (BusinessException e) {
            System.out.println("Ошибка: " + e.getMessage());
        }
    }
}
```
Старый (длинный) способ передать проверку — **анонимный класс**:
```java
String email = readValidString("Введите email: ", new UnaryOperator<String>() {
    @Override
    public String apply(String s) {
        return Validator.validateEmail(s);
    }
});
```
Работает, но пять строк обвязки ради одного вызова.

### Шаг 3. Лямбда и ссылка на метод

Java понимает: у `UnaryOperator` **всего один** метод, значит, понятно, что мы реализуем.
Поэтому всю обвязку можно выкинуть:
```java
String email = readValidString("Введите email: ", s -> Validator.validateEmail(s));
```
`s -> Validator.validateEmail(s)` — это **лямбда**: «принимаю `s`, возвращаю
`Validator.validateEmail(s)`». Слева от стрелки параметры, справа результат.

И ещё короче. Если лямбда просто передаёт параметр в другой метод, пишут **ссылку на метод**:
```java
String email = readValidString("Введите email: ", Validator::validateEmail);
```
`::` читается как «метод `validateEmail` класса `Validator`». Именно так написано в
нашем `Main.createGuest`.

**Все три варианта делают одно и то же.** Лямбда — просто короткая запись.

### Ещё примеры из проекта

| Запись | То же самое словами |
|---|---|
| `b -> b.getStatus() == status` | «для брони `b` вернуть: её статус равен искомому?» |
| `() -> new EntityNotFoundException("...")` | «без параметров; создать и вернуть исключение» |
| `User::getId` | `u -> u.getId()` |
| `System.out::println` | `x -> System.out.println(x)` |
| `id -> Validator.validateId(id, "гостя")` | «проверить id, передав ещё слово "гостя"» |

### Интерфейсы, которые мы используем

| Интерфейс | «Форма» метода | Когда нужен |
|---|---|---|
| `UnaryOperator<String>` | строка → строка | проверить и вернуть очищенное значение |
| `IntConsumer` | число → ничего | проверить число (бросит исключение, если плохое) |
| `Consumer<LocalDate>` | дата → ничего | проверить дату |
| `Predicate<Booking>` | бронь → да/нет | условие в `filter` |
| `Supplier<Exception>` | ничего → объект | «создай исключение, если понадобится» в `orElseThrow` |

### Почему в `orElseThrow` лямбда, а не сразу `new Exception`?

```java
.orElseThrow(() -> new EntityNotFoundException("..."))
```
Исключение создаётся **только если коробка пустая**. Лямбда — это «инструкция на случай,
если понадобится», а не готовый объект. Если гость найден, лишний объект не создаётся.

---

## 9. Stream API по шагам на наших данных

### 9.1 Идея: конвейер

Аналогия: **конвейер на заводе**. Детали едут по ленте, на каждом посту с ними что-то
делают: отбраковывают, сортируют, упаковывают.

```java
список.stream()        // поставить все элементы на ленту
      .filter(...)     // пост «пропустить только подходящие»
      .sorted(...)     // пост «упорядочить»
      .collect(...)    // пост «сложить в коробку (список)»
```

### 9.2 Пример 1: фильтр по статусу — `filterByStatus(CONFIRMED)`

```java
return bookingRepository.findAll().stream()
        .filter(b -> b.getStatus() == status)
        .collect(Collectors.toList());
```

Наши 12 бронирований из `init.sql` едут по ленте:

| # | Статус | `b.getStatus() == CONFIRMED`? | Проходит? |
|---|---|---|---|
| 1 | CONFIRMED | true | ✅ |
| 2 | CREATED | false | ❌ |
| 3 | CHECKED_OUT | false | ❌ |
| 4 | CONFIRMED | true | ✅ |
| 5 | CANCELLED | false | ❌ |
| 6 | CHECKED_IN | false | ❌ |
| 7 | CHECKED_OUT | false | ❌ |
| 8 | CREATED | false | ❌ |
| 9 | CONFIRMED | true | ✅ |
| 10 | CREATED | false | ❌ |
| 11 | CANCELLED | false | ❌ |
| 12 | CONFIRMED | true | ✅ |

Результат: `[#1, #4, #9, #12]`.

Для сравнения, то же самое без стримов:
```java
List<Booking> result = new ArrayList<>();
for (Booking b : bookingRepository.findAll()) {
    if (b.getStatus() == status) {
        result.add(b);
    }
}
return result;
```
Делает ровно то же. Стрим — короче и читается как описание: «отфильтруй, собери».

### 9.3 Пример 2: сортировка по цене (дорогие сначала)

```java
.sorted(Comparator.comparingDouble(Booking::getTotalPrice).reversed())
```
- `Comparator.comparingDouble(Booking::getTotalPrice)` — «сравнивай брони по цене»
  (по возрастанию: дешёвые сначала);
- `.reversed()` — «наоборот»: дорогие сначала.

Результат на наших данных: #6 (22500), #4 (20000), #12 (10500), #9 (10000), ...

### 9.4 Пример 3: статистика — несколько стримов

```java
long cancelled = bookings.stream()
        .filter(b -> b.getStatus() == BookingStatus.CANCELLED)
        .count();                     // #5 и #11 → 2
```

```java
double totalRevenue = bookings.stream()
        .filter(b -> b.getStatus() != BookingStatus.CANCELLED)   // выкинуть отменённые (#5, #11)
        .mapToDouble(Booking::getTotalPrice)                      // бронь → её цена (число)
        .sum();                                                   // сложить
```
`mapToDouble` — пост «превращения»: на ленту заехала бронь, а уехало число (её цена).
После этого можно вызвать `sum()` или `average()`.

### 9.5 Пример 4: занят ли номер — `anyMatch`

Кто-то хочет забронировать номер 101 с 2026-10-02 по 2026-10-05.
В базе есть бронь #1: номер 101, с 2026-10-01 по 2026-10-04, CONFIRMED.

```java
boolean occupied = bookingRepository.findAll().stream()
        .filter(b -> b.getRoomNumber() == roomNumber)                 // остаётся только #1
        .filter(b -> b.getStatus() != BookingStatus.CANCELLED)        // #1 не отменена, остаётся
        .filter(b -> excludeBookingId == null || b.getId() != excludeBookingId)   // при создании null → остаётся
        .anyMatch(b -> checkIn.isBefore(b.getCheckOutDate()) && checkOut.isAfter(b.getCheckInDate()));
```
Проверка последней строки для #1:
- `checkIn.isBefore(b.getCheckOutDate())` → 02.10 раньше 04.10? **да**
- `checkOut.isAfter(b.getCheckInDate())` → 05.10 позже 01.10? **да**
- оба «да» → даты пересекаются → `anyMatch` возвращает `true` → номер занят → исключение.

`anyMatch` останавливается на **первом** совпадении: остальные брони проверять не нужно.

**Почему именно такая формула?** Два отрезка **не** пересекаются только в двух случаях:
новый целиком раньше (`checkOut <= старый заезд`) или целиком позже (`checkIn >= старый выезд`).
Во всех остальных случаях пересекаются. Формула — отрицание этих двух случаев.

```
Старая бронь:        |=========|
                    01.10    04.10
Новая (02–05):          |=========|      ← пересекается
Новая (04–06):                 |=====|   ← НЕ пересекается: выезд 04, заезд 04 — можно
```

### 9.6 Пример 5: `Collectors.toMap`

```java
Map<Integer, User> usersById = userRepository.findAll().stream()
        .collect(Collectors.toMap(User::getId, u -> u));
```
«Собери в `Map`: ключ — `id` гостя, значение — сам гость».
Результат: `{1 → Иванов, 2 → Петрова, 3 → Сидоров, 4 → Кузнецова, 5 → Смирнов}`.
Потом по `b.getUserId()` любую бронь можно мгновенно связать с гостем.

---

## 10. Даты

```java
LocalDate checkIn = LocalDate.parse("2026-10-01");   // строка → дата
LocalDate today = LocalDate.now();                   // сегодня
checkIn.isBefore(today)                               // раньше ли?
checkIn.plusDays(3)                                   // 2026-10-04 (новый объект!)
ChronoUnit.DAYS.between(checkIn, checkOut)            // сколько дней между
```

**Неизменяемость.** Этот код ничего не делает:
```java
checkIn.plusDays(3);          // результат выброшен
```
Правильно:
```java
LocalDate later = checkIn.plusDays(3);
```
Дата, как и `String`, никогда не меняется. Все методы возвращают **новый** объект.

**Защита от несуществующих дат:** `LocalDate.parse("2026-02-30")` бросает
`DateTimeParseException`. В `Main.readDate` это ловится, и дата переспрашивается.

---

## 11. Регулярные выражения: читаем по символу

Регулярное выражение — это **шаблон**, с которым сравнивается строка. Как маска
«__.__.____» для даты, только гибче.

### 11.1 Телефон

```java
Pattern.compile("^(?:\\+7|8)(\\d{10})$")
```
Двойные `\\` нужны из-за Java-строки: чтобы в шаблон попал один `\`, в строке пишут `\\`.
Сам шаблон: `^(?:\+7|8)(\d{10})$`. Разбираем:

| Кусок | Значение |
|---|---|
| `^` | начало строки (перед ним ничего не должно быть) |
| `(?:` ... `)` | группа (скобки, как в математике), `?:` = «не запоминать» |
| `\+7` | символы `+7` (плюс экранирован, иначе он значит «1 и более») |
| `\|` | ИЛИ |
| `8` | символ `8` |
| `(\d{10})` | ровно 10 цифр, **запомнить** их как группу №1 |
| `$` | конец строки (после ничего не должно быть) |

Словами: «начинается с `+7` или с `8`, дальше ровно 10 цифр, и всё».

Как работает `validatePhone` для ввода `8 (916) 111-22-33`:
1. Проверка допустимых символов `[0-9+()\-\s]+` — только цифры, `+`, скобки, дефис,
   пробелы. ✅
2. Удаляем «украшения»: `replaceAll("[\\s()\\-]", "")` → `89161112233`.
3. Сравниваем с шаблоном: начинается с `8`, дальше `9161112233` (10 цифр). ✅
4. `matcher.group(1)` → `9161112233` (то, что в скобках без `?:`).
5. Собираем красивый вид: `"+7 " + "916" + " " + "111" + "-" + "22" + "-" + "33"`
   → `+7 916 111-22-33`.

Почему `^` и `$` важны? Без них `\d{10}` нашло бы 10 цифр **где-то внутри** строки,
и `abc89161112233xyz` прошёл бы. Метод `matches()` в Java, впрочем, и так требует совпадения
всей строки, а `^...$` — для наглядности и надёжности.

### 11.2 Паспорт

`^\d{10}$` — «ровно 10 цифр, больше ничего». Перед проверкой удаляем пробелы:
`4601 777888` → `4601777888`.

### 11.3 ФИО (упрощённо)

Слово — это `[A-Za-zА-Яа-яЁё]+` («одна или больше букв»). Внутри слова разрешены
`-` или `'`, если после них снова буквы: `Салтыков-Щедрин`, `O'Neil`. Слов — от 2 до 4,
через пробел.

| Ввод | Результат | Почему |
|---|---|---|
| `Иванов Иван` | ✅ | два слова из букв |
| `Иванов` | ❌ | одно слово |
| `Иванов 123` | ❌ | цифры |
| `Иванов Иван!` | ❌ | `!` не буква |
| `Салтыков-Щедрин Михаил` | ✅ | дефис внутри слова разрешён |

`Ё` и `ё` перечислены отдельно, потому что в таблице символов они стоят не между `А` и `я`,
и диапазон `А-я` их не включает.

### 11.4 Шпаргалка

| Символ | Значит | Пример |
|---|---|---|
| `\d` | цифра | `\d\d` → `42` |
| `[abc]` | один из перечисленных | `[+-]` → `+` или `-` |
| `[a-z]` | один из диапазона | |
| `+` | 1 или больше раз | `\d+` → `7`, `12345` |
| `*` | 0 или больше | |
| `?` | 0 или 1 | |
| `{3}` / `{1,3}` | ровно 3 / от 1 до 3 | |
| `^` `$` | начало / конец | |
| `( )` | группа | |
| `\|` | или | `(\+7\|8)` |

---

## 12. static и final

### 12.1 static — «общее для всех, принадлежит классу»

Аналогия: класс `Студент`. У каждого студента своё имя (обычное поле). А «название
университета» одно на всех, его не нужно хранить в каждом студенте (static).

```java
Validator.validateEmail("a@b.ru");     // вызываем у КЛАССА, объект не создаём
DatabaseManager.getConnection();       // то же
```
А вот `userService.createUser(...)` — у **объекта**, потому что у сервиса есть своё
состояние (поле `userRepository`).

Правило: если методу не нужны поля объекта, его можно сделать `static`. Методам
`Validator` нужны только параметры, поэтому они статические.

### 12.2 final — «присвоить один раз»

```java
private final UserRepository userRepository;   // задаётся в конструкторе, потом не меняется
```
Если где-то написать `userRepository = null;`, будет ошибка компиляции. Защита от
случайной порчи.

`final` + `static` = **константа**:
```java
public static final int MAX_NIGHTS = 30;
private static final String URL = "jdbc:postgresql://localhost:5432/hotel_db";
```
Константы называют `БОЛЬШИМИ_БУКВАМИ_С_ПОДЧЁРКИВАНИЕМ`.

**Тонкость:** `final` запрещает менять **ссылку**, а не объект:
```java
final List<String> list = new ArrayList<>();
list.add("x");        // можно — меняем содержимое объекта
list = new ArrayList<>();   // нельзя — меняем саму ссылку
```

### 12.3 Утилитный класс

```java
public final class Validator {       // final — нельзя наследоваться
    private Validator() { }          // private-конструктор — нельзя сделать new Validator()
    public static String validateEmail(...) { ... }
}
```
Это «ящик с инструментами»: создавать объект бессмысленно, всё вызывается через имя класса.

---

## 13. switch со стрелками

```java
switch (choice) {
    case "1" -> guestsMenu();
    case "2" -> bookingsMenu();
    case "0" -> running = false;
    default  -> System.out.println("Неизвестный пункт меню.");
}
```
Выполняется **только одна** ветка, чья метка совпала. Если не совпала ни одна, выполняется
`default`.

Старый `switch` (с двоеточием) без `break` «проваливался» в следующие ветки:
```java
case "1": guestsMenu();      // забыли break →
case "2": bookingsMenu();    // выполнится и это!
```
Со стрелками такой ошибки не бывает.

---

## 14. Scanner и надёжный ввод

```java
private static int readInt(String prompt) {
    while (true) {                                   // бесконечный цикл...
        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        try {
            return Integer.parseInt(input);          // ...из которого выходим через return, когда всё хорошо
        } catch (NumberFormatException e) {
            System.out.println("Ошибка: нужно ввести целое число. Попробуйте снова.");
        }                                            // иначе — новый круг
    }
}
```
Трассировка при вводе `abc`, потом `5`:
1. Круг 1: `input = "abc"`, `parseInt` бросает `NumberFormatException`, сообщение, новый круг.
2. Круг 2: `input = "5"`, `parseInt` → `5`, `return 5`, выход из цикла и метода.

`readValidInt` — надстройка: сначала `readInt` (число ли это?), потом проверка
(подходит ли число, например номер комнаты 101–999?).

```
Ввод "abc"  → readInt: не число → переспросить
Ввод "5"    → readInt: ok → Validator.validateRoomNumber(5): плохо → переспросить
Ввод "205"  → readInt: ok → validateRoomNumber(205): ok → вернуть 205
```

---

## 15. JDBC: разговор Java с базой данных

Аналогия: **телефонный звонок**.

| Шаг | Звонок | JDBC |
|---|---|---|
| 1 | набрать номер | `DriverManager.getConnection(URL, USER, PASSWORD)` → `Connection` |
| 2 | подготовить вопрос с пропусками | `connection.prepareStatement("SELECT * FROM users WHERE id = ?")` |
| 3 | заполнить пропуски | `statement.setInt(1, 5)` |
| 4 | задать вопрос | `statement.executeQuery()` → `ResultSet` |
| 5 | выслушать ответ построчно | `while (resultSet.next()) { ... }` |
| 6 | положить трубку | `try-with-resources` закрывает всё сам |

### Разбор `UserRepository.findById(5)`

```java
String sql = "SELECT * FROM users WHERE id = ?";
try (Connection connection = DatabaseManager.getConnection();
     PreparedStatement statement = connection.prepareStatement(sql)) {

    statement.setInt(1, id);                       // первый (и единственный) ? заменить на 5
    try (ResultSet resultSet = statement.executeQuery()) {
        if (resultSet.next()) {                    // есть ли строка?
            return Optional.of(mapRow(resultSet)); // да → превратить в User и положить в коробку
        }
    }
}
return Optional.empty();                           // строк нет → пустая коробка
```

**`ResultSet` — как курсор в таблице.** Сначала он стоит **перед** первой строкой.
`next()` сдвигает его на следующую и возвращает `true`, если строка есть.
```
          ┌────┬──────────────────────────┬───────
курсор →  │ id │ full_name                │ ...
(до next) ├────┼──────────────────────────┼───────
next() →  │ 5  │ Смирнов Дмитрий Олегович │ ...
          └────┴──────────────────────────┴───────
next() → false (строк больше нет)
```

**`mapRow`** читает поля текущей строки по именам столбцов:
```java
new User(
    resultSet.getInt("id"),
    resultSet.getString("full_name"),
    ...
);
```

### Почему `?`, а не склеивать строку — SQL-инъекция

Допустим, поиск по email написан склейкой:
```java
String sql = "SELECT * FROM users WHERE email = '" + email + "'";
```
Пользователь вводит: `' OR '1'='1`. Получается SQL:
```sql
SELECT * FROM users WHERE email = '' OR '1'='1'
```
`'1'='1'` всегда истинно, и запрос вернёт **всех** гостей. А с вводом `'; DROP TABLE users; --`
можно было бы удалить таблицу.

С `?` введённый текст передаётся базе **отдельно**, как данные. Он никогда не станет частью
SQL-команды, даже если в нём кавычки. Поэтому у нас все запросы с `?`.

### Как новый гость получает id

```java
PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
statement.executeUpdate();                      // INSERT
try (ResultSet keys = statement.getGeneratedKeys()) {
    if (keys.next()) {
        user.setId(keys.getInt(1));             // база выдала id (SERIAL) — записываем в объект
    }
}
```
До `save` у объекта `id = 0`, после — настоящий (`6`, `7`, ...).

---

## 16. Maven

Без Maven пришлось бы вручную скачивать `.jar` драйвера PostgreSQL и Apache POI (а у POI
ещё десяток зависимостей), класть их в папку и прописывать в командах компиляции и запуска.

С Maven достаточно написать в `pom.xml`:
```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.3</version>
</dependency>
```
и Maven сам всё скачает (в папку `C:\Users\<имя>\.m2`), включая «зависимости зависимостей».

`mvn clean package`:
1. `clean` — удалить старую папку `target`;
2. `compile` — `.java` → `.class` (байт-код);
3. `test` — запустить тесты (у нас их нет в проекте);
4. `package` — упаковать в `.jar`. Плагин `shade` кладёт внутрь и все библиотеки,
   поэтому хватает одной команды `java -jar target/hotel-booking-system.jar`.

---

## 17. Проверь себя

Ответы — под вопросами, сначала попробуйте сами.

**1. Что выведет код?**
```java
User a = userService.getUserById(1);
User b = userService.getUserById(1);
System.out.println(a == b);
System.out.println(a.equals(b));
```
<details><summary>Ответ</summary>

`false`, потом `true`. `findById` каждый раз создаёт новый объект (`new User` в `mapRow`),
значит, адреса разные. Но `equals` сравнивает `id`, а он одинаковый.
</details>

**2. Что сломается, если удалить из `User` метод `hashCode`, оставив `equals`?**
<details><summary>Ответ</summary>

`HashSet`/`HashMap` перестанут находить «равных» гостей: два объекта с одинаковым `id`
получат разные `hashCode` и попадут в разные «ячейки». `contains` вернёт `false`.
</details>

**3. Почему `BusinessException` наследуется от `RuntimeException`, а не от `Exception`?**
<details><summary>Ответ</summary>

Чтобы исключение было непроверяемым. Иначе каждый метод, который может его бросить
(и все, кто их вызывает), обязан был бы писать `throws BusinessException`.
</details>

**4. Что произойдёт с гостем, если в `createUser` сработала проверка уникальности?**
<details><summary>Ответ</summary>

`throw` прервёт метод до строки `userRepository.save(user)`, и гость не будет сохранён.
Исключение долетит до `mainMenu`, там его поймают и покажут сообщение.
</details>

**5. Перепишите без стрима:**
```java
long cancelled = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).count();
```
<details><summary>Ответ</summary>

```java
long cancelled = 0;
for (Booking b : bookings) {
    if (b.getStatus() == BookingStatus.CANCELLED) {
        cancelled++;
    }
}
```
</details>

**6. Можно ли забронировать номер 101 с 2026-10-04 по 2026-10-06, если есть бронь #1 (101, 01.10–04.10, CONFIRMED)?**
<details><summary>Ответ</summary>

Да. Проверка: `04.10 isBefore 04.10` → `false`. Условие пересечения не выполнено:
один гость выезжает 4-го, другой в тот же день заезжает.
</details>

**7. Что значит `Validator::validateEmail`?**
<details><summary>Ответ</summary>

Ссылка на метод, короткая запись лямбды `s -> Validator.validateEmail(s)`. Передаёт
«действие проверки» как параметр в `readValidString`.
</details>

**8. Почему `input == "1"` — ошибка, а `switch (input) { case "1" -> ... }` работает?**
<details><summary>Ответ</summary>

`==` сравнивает адреса объектов-строк, а строка с клавиатуры — всегда новый объект.
`switch` по строкам внутри сравнивает через `equals` (и `hashCode`).
</details>

**9. Зачем `PreparedStatement` с `?`?**
<details><summary>Ответ</summary>

Значения передаются отдельно от текста SQL, поэтому введённые кавычки и команды
не выполняются. Это защита от SQL-инъекций.
</details>

**10. Что вернёт `userRepository.findById(999)`, и что с этим сделает сервис?**
<details><summary>Ответ</summary>

`Optional.empty()` (пустую коробку). Сервис вызовет `orElseThrow(...)` и бросит
`EntityNotFoundException("Гость с ID 999 не найден.")`.
</details>
