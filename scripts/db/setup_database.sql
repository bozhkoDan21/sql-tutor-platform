-- ============================================
-- ЕДИНЫЙ СКРИПТ НАСТРОЙКИ БАЗ ДАННЫХ
-- ============================================

-- Создаем базы данных
CREATE DATABASE sql_tutor_university_db;
CREATE DATABASE archaeology_10m;

-- ============================================
-- НАСТРОЙКА ПЕРВОЙ БАЗЫ (Университетская)
-- ============================================

\c sql_tutor_university_db;

-- Расширения
CREATE EXTENSION IF NOT EXISTS plpython3u;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Справочные таблицы
CREATE TABLE city (
                      id SERIAL PRIMARY KEY,
                      name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE educational_program (
                                     id SERIAL PRIMARY KEY,
                                     name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE employee (
                          id SERIAL PRIMARY KEY,
                          full_name VARCHAR(255) NOT NULL
);

CREATE TABLE faculty (
                         id SERIAL PRIMARY KEY,
                         name VARCHAR(255) NOT NULL UNIQUE,
                         dean_id INTEGER NOT NULL REFERENCES employee(id),
                         seats_count INTEGER NOT NULL CHECK (seats_count > 0)
);

-- Таблица групп
CREATE TABLE student_group (
                               id SERIAL PRIMARY KEY,
                               name VARCHAR(50) NOT NULL,
                               faculty_id INTEGER NOT NULL REFERENCES faculty(id),
                               educational_program_id INTEGER NOT NULL REFERENCES educational_program(id),
                               UNIQUE (name, faculty_id)
);

-- Основная таблица студентов (1 млн)
CREATE TABLE student (
                         id SERIAL PRIMARY KEY,
                         full_name VARCHAR(255) NOT NULL,
                         birth_date DATE NOT NULL CHECK (birth_date BETWEEN '1950-01-01' AND CURRENT_DATE),
                         city_id INTEGER NOT NULL REFERENCES city(id)
);

-- Таблица зачислений (10 млн)
CREATE TABLE enrollment (
                            id SERIAL PRIMARY KEY,
                            student_id INTEGER NOT NULL,
                            group_id INTEGER NOT NULL,
                            faculty_id INTEGER NOT NULL,
                            year_of_enrollment INTEGER NOT NULL CHECK (year_of_enrollment BETWEEN 2000 AND EXTRACT(YEAR FROM CURRENT_DATE) + 1),
                            scholarship_amount DECIMAL(10,2) DEFAULT NULL,
                            notes TEXT,
                            created_at DATE DEFAULT CURRENT_DATE
);

-- Функция для добавления внешних ключей ПОСЛЕ генерации
CREATE OR REPLACE FUNCTION add_constraints_after_generation()
    RETURNS VOID AS $$
BEGIN
    -- Внешние ключи для enrollment
ALTER TABLE enrollment ADD CONSTRAINT enrollment_student_id_fkey
    FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE CASCADE;

ALTER TABLE enrollment ADD CONSTRAINT enrollment_group_id_fkey
    FOREIGN KEY (group_id) REFERENCES student_group(id) ON DELETE CASCADE;

ALTER TABLE enrollment ADD CONSTRAINT enrollment_faculty_id_fkey
    FOREIGN KEY (faculty_id) REFERENCES faculty(id);

RAISE NOTICE 'Все ограничения успешно добавлены';
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- ГЕНЕРАТОР ДАННЫХ НА PL/PYTHON ДЛЯ УНИВЕРСИТЕТСКОЙ БАЗЫ
-- ============================================

CREATE OR REPLACE FUNCTION generate_university_data()
    RETURNS text
    AS $$

import random
from datetime import datetime, timedelta
import traceback
import time

# ======================================
# КОНФИГУРАЦИЯ ДАННЫХ
# ======================================

CITIES = [
    "Москва", "Санкт-Петербург", "Новосибирск", "Екатеринбург", "Казань",
    "Нижний Новгород", "Челябинск", "Самара", "Омск", "Ростов-на-Дону",
    "Уфа", "Красноярск", "Воронеж", "Пермь", "Волгоград"
]

EMPLOYEES = [
    "Иванов Иван Иванович", "Петров Петр Петрович", "Сидоров Сидор Сидорович",
    "Смирнова Анна Александровна", "Кузнецов Дмитрий Николаевич",
    "Попова Елена Владимировна", "Соколов Андрей Михайлович",
    "Лебедева Ольга Викторовна", "Козлов Алексей Сергеевич", "Новикова Татьяна Павловна"
]

PROGRAMS = [
    "Прикладная математика", "Информатика и вычислительная техника",
    "Программная инженерия", "Информационная безопасность",
    "Физика", "Химия", "Биология", "Экономика", "Менеджмент", "Лингвистика"
]

FACULTIES = [
    (1, "Факультет математики и информатики", 100),
    (2, "Физический факультет", 75),
    (3, "Химический факультет", 60),
    (4, "Биологический факультет", 65),
    (5, "Экономический факультет", 80),
    (6, "Факультет гуманитарных наук", 70)
]

GROUP_PREFIXES = {
    1: "ИВТ",  # Математика и информатика
    2: "ФИЗ",  # Физика
    3: "ХИМ",  # Химия
    4: "БИО",  # Биология
    5: "ЭК",   # Экономика
    6: "ЛИН"   # Лингвистика
}

# ======================================
# ГЕНЕРАЦИЯ ИМЕН (>1000 вариантов)
# ======================================

FIRST_NAMES_MALE = [
    "Александр", "Андрей", "Борис", "Василий", "Виктор", "Владимир", "Вячеслав",
    "Геннадий", "Григорий", "Денис", "Дмитрий", "Евгений", "Иван", "Игорь",
    "Кирилл", "Константин", "Леонид", "Максим", "Михаил", "Никита", "Николай",
    "Олег", "Павел", "Петр", "Роман", "Сергей", "Станислав", "Степан", "Федор", "Юрий"
]

FIRST_NAMES_FEMALE = [
    "Александра", "Алина", "Алла", "Анастасия", "Анна", "Валентина", "Валерия",
    "Вера", "Виктория", "Галина", "Дарья", "Евгения", "Екатерина", "Елена",
    "Елизавета", "Ирина", "Ксения", "Любовь", "Людмила", "Маргарита", "Марина",
    "Мария", "Надежда", "Наталья", "Нина", "Оксана", "Ольга", "Светлана", "Татьяна", "Юлия"
]

LAST_NAMES = [
    "Иванов", "Петров", "Сидоров", "Смирнов", "Кузнецов", "Попов", "Васильев",
    "Михайлов", "Федоров", "Соколов", "Алексеев", "Лебедев", "Козлов", "Новиков",
    "Морозов", "Волков", "Соловьев", "Воробьев", "Тимофеев", "Ковалев",
    "Николаев", "Андреев", "Макаров", "Зайцев", "Егоров", "Павлов", "Семенов",
    "Голубев", "Виноградов", "Богданов", "Воробьев", "Федотов", "Михайлов",
    "Беляев", "Тарасов", "Белов", "Комаров", "Орлов", "Киселев", "Макаров",
    "Андреев", "Ковалев", "Ильин", "Гусев", "Титов", "Кузьмин", "Кудрявцев",
    "Баранов", "Куликов", "Алексеев", "Степанов", "Яковлев", "Сорокин", "Сергеев",
    "Романов", "Захаров", "Борисов", "Королев", "Герасимов", "Пономарев",
    "Григорьев", "Лазарев", "Медведев", "Ершов", "Никитин", "Соболев", "Рябов",
    "Поляков", "Цветков", "Данилов", "Жуков", "Фролов", "Журавлев", "Николаев",
    "Крылов", "Максимов", "Сидоров", "Осипов", "Белоусов", "Федотов", "Дорофеев",
    "Егоров", "Матвеев", "Бобров", "Дмитриев", "Калинин", "Анисимов", "Петухов",
    "Антонов", "Тимофеев", "Никифоров", "Веселов", "Филиппов", "Марков", "Большаков",
    "Суханов", "Миронов", "Ширяев", "Александров", "Коновалов", "Шестаков", "Казаков"
]

# ======================================
# ШАБЛОНЫ ДЛЯ ЗАМЕТОК (>1000 вариантов)
# ======================================

NOTE_SUBJECTS = [
    "математике", "физике", "химии", "биологии", "информатике",
    "программированию", "базам данных", "сетям", "английскому языку",
    "истории", "философии", "экономике", "менеджменту", "праву"
]

NOTE_REASONS = [
    "состоянию здоровья", "семейным обстоятельствам", "призыву в армию",
    "академическому отпуску", "переводу в другой вуз", "стажировке за рубежом"
]

NOTE_COMPANIES = [
    "Яндекс", "Сбер", "VK", "Газпром", "Росатом", "Лаборатория Касперского",
    "1С", "Mail.ru", "Тинькофф", "Ozon", "Wildberries", "Ростелеком"
]

NOTE_CONFERENCES = [
    "Студенческая наука", "ИТ-перспективы", "Молодые исследователи",
    "Цифровая экономика", "Инновации в образовании", "Будущее за нами"
]

NOTE_TEMPLATES = [
    "Академическая задолженность по {subject}. Необходимо пересдать до {date}.",
    "Успешно сдал(а) сессию на отлично! Поздравляем!",
    "Переведен(а) из группы {old_group} в группу {new_group}.",
    "Академический отпуск по {reason} с {date}. Окончание: {end_date}.",
    "Участие в конференции {conference}. Тема доклада: {topic}.",
    "Победитель олимпиады по {subject}. Присуждена повышенная стипендия.",
    "Стажировка в компании {company}. Период: {period} месяцев.",
    "Стипендия повышена до {amount} руб. за успехи в учебе.",
    "Заявление на общежитие: {status}. Комната №{room}.",
    "Ходатайство о переводе на бюджет одобрено. Приказ №{order}.",
    "Академическая мобильность: {country}, {university}, семестр {semester}.",
    "Участие в волонтерском проекте {project}. Часов: {hours}.",
    "Публикация статьи в журнале {journal}: {title}.",
    "Грант на исследование по теме {topic} в размере {grant} руб.",
    "Дисциплинарное взыскание за {violation}. Выговор.",
    "Благодарность за активное участие в жизни факультета.",
    "Пересдача экзамена по {subject} назначена на {date}.",
    "Индивидуальный учебный план утвержден. Куратор: {curator}.",
    "Зачислен(а) на военную кафедру. Специальность: {specialty}.",
    "Участие в спортивных соревнованиях {sport}. Результат: {result}."
]

def generate_note():
    """Генерация заметки для полнотекстового поиска (>1000 комбинаций)"""
    template = random.choice(NOTE_TEMPLATES)

    replacements = {
        "{subject}": random.choice(NOTE_SUBJECTS),
        "{reason}": random.choice(NOTE_REASONS),
        "{company}": random.choice(NOTE_COMPANIES),
        "{conference}": random.choice(NOTE_CONFERENCES),
        "{date}": (datetime.now() + timedelta(days=random.randint(-30, 90))).strftime("%d.%m.%Y"),
        "{end_date}": (datetime.now() + timedelta(days=random.randint(90, 365))).strftime("%d.%m.%Y"),
        "{old_group}": f"ГР-{random.randint(100, 999)}",
        "{new_group}": f"ГР-{random.randint(100, 999)}",
        "{amount}": str(random.randint(3000, 20000)),
        "{status}": random.choice(["одобрено", "отказано", "на рассмотрении"]),
        "{room}": str(random.randint(100, 999)),
        "{order}": str(random.randint(1000, 9999)),
        "{country}": random.choice(["Германия", "Франция", "Италия", "Испания", "Китай"]),
        "{university}": random.choice(["TU Berlin", "Sorbonne", "Politecnico", "Tsinghua"]),
        "{semester}": str(random.randint(1, 4)),
        "{project}": random.choice(["Помощь пожилым", "Экология", "Образование"]),
        "{hours}": str(random.randint(10, 200)),
        "{journal}": random.choice(["Вестник науки", "Молодой ученый", "Студенческий"]),
        "{title}": random.choice(["Исследование...", "Анализ...", "Разработка..."]),
        "{grant}": str(random.randint(50000, 500000)),
        "{violation}": random.choice(["опоздание", "пропуск", "неуспеваемость"]),
        "{curator}": random.choice(["Иванов", "Петров", "Сидоров"]),
        "{specialty}": random.choice(["связист", "радист", "программист"]),
        "{sport}": random.choice(["футбол", "баскетбол", "волейбол", "шахматы"]),
        "{result}": random.choice(["1 место", "2 место", "3 место", "участие"]),
        "{topic}": random.choice([
            "Машинное обучение", "Big Data", "Кибербезопасность",
            "Искусственный интеллект", "Робототехника"
        ]),
        "{period}": str(random.randint(1, 12))
    }

    note = template
    for key, value in replacements.items():
        if key in note:
            note = note.replace(key, value)

    return note.replace("'", "''")

# ======================================
# ФУНКЦИИ ГЕНЕРАЦИИ
# ======================================

def generate_cities():
    plpy.info("Генерация городов...")
    for city in CITIES:
        plpy.execute(f"INSERT INTO city (name) VALUES ('{city}')")

def generate_educational_programs():
    plpy.info("Генерация образовательных программ...")
    for program in PROGRAMS:
        plpy.execute(f"INSERT INTO educational_program (name) VALUES ('{program}')")

def generate_employees():
    plpy.info("Генерация сотрудников...")
    for emp in EMPLOYEES:
        plpy.execute(f"INSERT INTO employee (full_name) VALUES ('{emp}')")

def generate_faculties():
    plpy.info("Генерация факультетов...")
    employees = plpy.execute("SELECT id FROM employee ORDER BY id")
    for i, (fac_id, fac_name, seats) in enumerate(FACULTIES):
        dean_id = employees[i]["id"]
        plpy.execute(f"INSERT INTO faculty (id, name, dean_id, seats_count) VALUES ({fac_id}, '{fac_name}', {dean_id}, {seats})")

def generate_groups():
    plpy.info("Генерация учебных групп...")
    faculties = plpy.execute("SELECT id FROM faculty")
    programs = plpy.execute("SELECT id FROM educational_program")

    group_id = 1
    for faculty in faculties:
        faculty_id = faculty["id"]
        prefix = GROUP_PREFIXES[faculty_id]
        # 3-5 групп на каждый факультет
        for i in range(random.randint(3, 5)):
            for program in programs:
                program_id = program["id"]
                # 2-3 группы на программу
                for j in range(random.randint(2, 3)):
                    group_num = random.randint(101, 999)
                    group_name = f"{prefix}-{group_num}"

                    check = plpy.execute(f"SELECT id FROM student_group WHERE name = '{group_name}' AND faculty_id = {faculty_id}")
                    if len(check) == 0:
                        plpy.execute(f"""
                            INSERT INTO student_group (id, name, faculty_id, educational_program_id)
                            VALUES ({group_id}, '{group_name}', {faculty_id}, {program_id})
                        """)
                        group_id += 1

def generate_students(num_students=1000000, batch_size=10000):
    plpy.info(f"Генерация студентов (1 млн записей)...")

    city_ids = [row["id"] for row in plpy.execute("SELECT id FROM city")]
    start_date = datetime(1995, 1, 1)
    end_date = datetime(2005, 12, 31)
    date_range = (end_date - start_date).days

    start_time = time.time()
    generated = 0

    for batch_start in range(0, num_students, batch_size):
        values = []
        batch_end = min(batch_start + batch_size, num_students)

        for _ in range(batch_end - batch_start):
            # Генерация ФИО
            is_male = random.random() > 0.5
            last_name = random.choice(LAST_NAMES)
            first_name = random.choice(FIRST_NAMES_MALE if is_male else FIRST_NAMES_FEMALE)

            if not is_male and last_name.endswith("в"):
                last_name = last_name[:-1] + "а"
            elif not is_male and last_name.endswith("н"):
                last_name += "а"

            patronymic = random.choice(["Александрович", "Андреевич", "Иванович", "Петрович", "Сергеевич"])
            if not is_male:
                patronymic = patronymic[:-2] + "на" if patronymic.endswith("ич") else patronymic + "на"

            full_name = f"{last_name} {first_name} {patronymic}"
            birth_date = (start_date + timedelta(days=random.randint(0, date_range))).strftime("%Y-%m-%d")
            city_id = random.choice(city_ids)

            values.append(f"('{full_name}', '{birth_date}', {city_id})")

        sql = f"INSERT INTO student (full_name, birth_date, city_id) VALUES {','.join(values)};"
        plpy.execute(sql)

        generated += len(values)
        elapsed = time.time() - start_time

        if generated % 100000 == 0:
            rate = generated / elapsed
            plpy.info(f"Сгенерировано {generated}/{num_students} студентов. Скорость: {int(rate)} записей/сек")

def generate_enrollments(num_records=10000000, batch_size=50000):
    plpy.info(f"Генерация записей об обучении (10 млн записей)...")

    student_ids = [row["id"] for row in plpy.execute("SELECT id FROM student")]
    groups = plpy.execute("""
        SELECT sg.id as group_id, sg.faculty_id
        FROM student_group sg
    """)

    group_list = [(row["group_id"], row["faculty_id"]) for row in groups]
    current_year = datetime.now().year

    start_time = time.time()
    generated = 0

    for batch_start in range(0, num_records, batch_size):
        values = []
        batch_end = min(batch_start + batch_size, num_records)

        for _ in range(batch_end - batch_start):
            student_id = random.choice(student_ids)
            group_id, faculty_id = random.choice(group_list)
            year = random.randint(2015, current_year)

            # 30% студентов имеют стипендию
            scholarship = round(random.uniform(2000, 10000), 2) if random.random() < 0.3 else "NULL"

            # 40% записей имеют заметки
            if random.random() < 0.4:
                note = generate_note()
                values.append(f"({student_id}, {group_id}, {faculty_id}, {year}, {scholarship}, '{note}')")
            else:
                values.append(f"({student_id}, {group_id}, {faculty_id}, {year}, {scholarship}, NULL)")

        sql = f"""
            INSERT INTO enrollment
            (student_id, group_id, faculty_id, year_of_enrollment, scholarship_amount, notes)
            VALUES {','.join(values)};
        """
        plpy.execute(sql)

        generated += len(values)
        elapsed = time.time() - start_time

        if generated % 1000000 == 0:
            rate = generated / elapsed
            plpy.info(f"Сгенерировано {generated}/{num_records} записей. Скорость: {int(rate)} записей/сек")

# ======================================
# ГЛАВНАЯ ФУНКЦИЯ ДЛЯ УНИВЕРСИТЕТСКОЙ БАЗЫ
# ======================================

def main_university():
    total_start_time = time.time()
    plpy.info("=" * 50)
    plpy.info("НАЧАЛО ГЕНЕРАЦИИ УНИВЕРСИТЕТСКОЙ БАЗЫ")
    plpy.info("=" * 50)

    plpy.info("1. Генерация справочников...")
    generate_cities()
    generate_educational_programs()
    generate_employees()
    generate_faculties()
    generate_groups()

    plpy.info("2. Генерация студентов (1 млн)...")
    generate_students(1000000, 10000)

    plpy.info("3. Генерация записей об обучении (10 млн)...")
    generate_enrollments(10000000, 50000)

    plpy.info("4. Добавление внешних ключей...")
    plpy.execute("SELECT add_constraints_after_generation()")

    total_time = time.time() - total_start_time
    plpy.info("=" * 50)
    plpy.info(f"ГЕНЕРАЦИЯ УНИВЕРСИТЕТСКОЙ БАЗЫ ЗАВЕРШЕНА ЗА {total_time:.2f} СЕК")
    plpy.info("=" * 50)

    return f"Университетская база готова! Время: {total_time:.2f} сек"

try:
    result = main_university()
    plpy.info(result)
except Exception as e:
    error_details = traceback.format_exc()
    plpy.error(f"ОШИБКА В УНИВЕРСИТЕТСКОЙ БАЗЕ: {str(e)}")
    plpy.error(error_details)

$$
LANGUAGE plpython3u;

-- ============================================
-- ИНДЕКСЫ ДЛЯ УНИВЕРСИТЕТСКОЙ БАЗЫ
-- ============================================

CREATE INDEX idx_student_optimal ON student(city_id, birth_date, full_name);
CREATE INDEX idx_enrollment_year_faculty ON enrollment(year_of_enrollment, faculty_id) WHERE year_of_enrollment >= 2020;
CREATE INDEX idx_student_birth ON student(birth_date);
CREATE INDEX idx_enrollment_student_scholarship ON enrollment(student_id) WHERE scholarship_amount IS NOT NULL;
CREATE INDEX idx_enrollment_fts ON enrollment USING gin(to_tsvector('russian', notes));

-- ============================================
-- НАСТРОЙКА ВТОРОЙ БАЗЫ (Археология)
-- ============================================

\c archaeology_10m;

-- Расширения
CREATE EXTENSION IF NOT EXISTS plpython3u;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ============================================
-- СПРАВОЧНЫЕ ТАБЛИЦЫ
-- ============================================

CREATE TABLE specializations (
                                 id INTEGER PRIMARY KEY,
                                 name VARCHAR(100) NOT NULL
);

CREATE TABLE qualifications (
                                id INTEGER PRIMARY KEY,
                                name VARCHAR(100) NOT NULL
);

CREATE TABLE locations (
                           id INTEGER PRIMARY KEY,
                           name VARCHAR(100) NOT NULL,
                           country VARCHAR(100),
                           region VARCHAR(100)
);

CREATE TABLE epochs (
                        id INTEGER PRIMARY KEY,
                        name VARCHAR(100) NOT NULL,
                        period VARCHAR(100)
);

CREATE TABLE owners (
                        id INTEGER PRIMARY KEY,
                        name VARCHAR(100) NOT NULL,
                        period VARCHAR(100)
);

CREATE TABLE artifact_types (
                                id INTEGER PRIMARY KEY,
                                name VARCHAR(100) NOT NULL,
                                description TEXT
);

CREATE TABLE conditions (
                            id INTEGER PRIMARY KEY,
                            name VARCHAR(100) NOT NULL,
                            description TEXT
);

-- ============================================
-- ЗАПОЛНЕНИЕ СПРАВОЧНИКОВ
-- ============================================

INSERT INTO specializations VALUES
                                (1, 'Классическая археология'), (2, 'Подводная археология'), (3, 'Средневековая археология'),
                                (4, 'Доисторическая археология'), (5, 'Библейская археология'), (6, 'Египтология'),
                                (7, 'Ассириология'), (8, 'Этрусология'), (9, 'Кельтология'), (10, 'Славянская археология'),
                                (11, 'Нумизматика'), (12, 'Эпиграфика'), (13, 'Палеография'), (14, 'Археозоология'),
                                (15, 'Археоботаника');

INSERT INTO qualifications VALUES
                               (1, 'Стажер'), (2, 'Младший научный сотрудник'), (3, 'Научный сотрудник'),
                               (4, 'Старший научный сотрудник'), (5, 'Ведущий научный сотрудник'), (6, 'Главный научный сотрудник'),
                               (7, 'Кандидат наук'), (8, 'Доктор наук'), (9, 'Профессор'), (10, 'Доцент'),
                               (11, 'Ассистент'), (12, 'Лаборант'), (13, 'Техник'), (14, 'Реставратор'), (15, 'Эксперт');

INSERT INTO locations VALUES
                          (1, 'Гиза', 'Египет', 'Каир'), (2, 'Рим', 'Италия', 'Лацио'), (3, 'Афины', 'Греция', 'Аттика'),
                          (4, 'Иерусалим', 'Израиль', 'Иудея'), (5, 'Стоунхендж', 'Великобритания', 'Уилтшир'),
                          (6, 'Мачу-Пикчу', 'Перу', 'Куско'), (7, 'Петра', 'Иордания', 'Маан'),
                          (8, 'Ангкор-Ват', 'Камбоджа', 'Сиемреап'), (9, 'Великий Зимбабве', 'Зимбабве', 'Масвинго'),
                          (10, 'Тикаль', 'Гватемала', 'Петен'), (11, 'Кносс', 'Греция', 'Крит'),
                          (12, 'Карфаген', 'Тунис', 'Тунис'), (13, 'Персеполь', 'Иран', 'Фарс'),
                          (14, 'Чичен-Ица', 'Мексика', 'Юкатан'), (15, 'Помпеи', 'Италия', 'Кампания');

INSERT INTO epochs VALUES
                       (1, 'Палеолит', '2.6 млн - 10 тыс. лет до н.э.'), (2, 'Мезолит', '10-6 тыс. лет до н.э.'),
                       (3, 'Неолит', '6-4 тыс. лет до н.э.'), (4, 'Бронзовый век', '3-1 тыс. лет до н.э.'),
                       (5, 'Железный век', '1 тыс. лет до н.э. - 1 век н.э.'), (6, 'Античность', '8 век до н.э. - 5 век н.э.'),
                       (7, 'Эллинизм', '4-1 века до н.э.'), (8, 'Римский период', '1 век до н.э. - 5 век н.э.'),
                       (9, 'Раннее Средневековье', '5-10 века'), (10, 'Высокое Средневековье', '11-13 века'),
                       (11, 'Позднее Средневековье', '14-15 века'), (12, 'Возрождение', '15-16 века'),
                       (13, 'Новое время', '17-18 века'), (14, 'Индустриальная эпоха', '19 век'),
                       (15, 'Современность', '20-21 века');

INSERT INTO owners VALUES
                       (1, 'Рамзес II', 'Новое царство'), (2, 'Александр Македонский', 'Эллинизм'),
                       (3, 'Юлий Цезарь', 'Римская республика'), (4, 'Карл Великий', 'Раннее Средневековье'),
                       (5, 'Вильгельм Завоеватель', 'Высокое Средневековье'), (6, 'Чингисхан', 'Монгольская империя'),
                       (7, 'Клеопатра', 'Эллинизм'), (8, 'Цинь Шихуанди', 'Империя Цинь'),
                       (9, 'Ашока', 'Империя Маурьев'), (10, 'Соломон', 'Израильское царство'),
                       (11, 'Константин Великий', 'Поздняя Римская империя'), (12, 'Аттила', 'Великое переселение народов'),
                       (13, 'Хаммурапи', 'Старовавилонский период'), (14, 'Тутмос III', 'Новое царство'),
                       (15, 'Перикл', 'Классическая Греция');

INSERT INTO artifact_types VALUES
                               (1, 'Керамика', 'Глиняные изделия и посуда'), (2, 'Металлические изделия', 'Предметы из бронзы, железа, золота'),
                               (3, 'Каменные орудия', 'Инструменты из камня'), (4, 'Ювелирные изделия', 'Украшения и драгоценности'),
                               (5, 'Оружие', 'Холодное и метательное оружие'), (6, 'Скульптура', 'Статуи и рельефы'),
                               (7, 'Монеты', 'Денежные единицы'), (8, 'Текстиль', 'Ткани и одежда'),
                               (9, 'Деревянные изделия', 'Предметы из дерева'), (10, 'Кости животных', 'Остатки фауны'),
                               (11, 'Стекло', 'Стеклянные изделия'), (12, 'Рукописи', 'Письменные документы'),
                               (13, 'Архитектурные элементы', 'Детали построек'), (14, 'Религиозные артефакты', 'Культовые предметы'),
                               (15, 'Бытовые предметы', 'Повседневные вещи');

INSERT INTO conditions VALUES
                           (1, 'Отличное', 'Практически без повреждений'), (2, 'Очень хорошее', 'Незначительные следы износа'),
                           (3, 'Хорошее', 'Заметные следы времени'), (4, 'Удовлетворительное', 'Значительные повреждения'),
                           (5, 'Плохое', 'Серьезные повреждения'), (6, 'Фрагментарное', 'Сохранилась часть артефакта'),
                           (7, 'Реставрированное', 'Восстановленный артефакт'), (8, 'Стабильное', 'Состояние не ухудшается'),
                           (9, 'Хрупкое', 'Требует особого обращения'), (10, 'Критическое', 'Необходима срочная реставрация');

-- ============================================
-- ОСНОВНЫЕ ТАБЛИЦЫ АРХЕОЛОГИИ
-- ============================================

CREATE TABLE archaeologists (
                                id SERIAL PRIMARY KEY,
                                first_name VARCHAR(100) NOT NULL,
                                last_name VARCHAR(100) NOT NULL,
                                patronymic VARCHAR(100),
                                specialization_id INTEGER REFERENCES specializations(id),
                                qualification_id INTEGER REFERENCES qualifications(id),
                                salary NUMERIC(10,2),
                                location_id INTEGER REFERENCES locations(id),
                                hire_date DATE
);

CREATE TABLE finds (
                       id SERIAL PRIMARY KEY,
                       name VARCHAR(255) NOT NULL,
                       discovery_date DATE,
                       archaeologist_id INTEGER REFERENCES archaeologists(id),
                       epoch_id INTEGER REFERENCES epochs(id),
                       owner_id INTEGER REFERENCES owners(id),
                       location_id INTEGER REFERENCES locations(id)
);

CREATE TABLE artifacts (
                           id SERIAL PRIMARY KEY,
                           find_id INTEGER REFERENCES finds(id),
                           type_id INTEGER REFERENCES artifact_types(id),
                           condition_id INTEGER REFERENCES conditions(id),
                           value NUMERIC(12,2),
                           description TEXT,
                           created_at DATE DEFAULT CURRENT_DATE,
                           material VARCHAR(100),
                           weight_grams NUMERIC(10,2),
                           height_cm NUMERIC(10,2),
                           width_cm NUMERIC(10,2),
                           depth_cm NUMERIC(10,2)
);

-- ============================================
-- ГЕНЕРАТОР ДАННЫХ ДЛЯ АРХЕОЛОГИИ
-- ============================================

CREATE OR REPLACE FUNCTION generate_archaeology_data()
RETURNS TEXT AS $$
import random
import time
from datetime import datetime, timedelta
import traceback

def generate_archaeologists(count=500):
    """Генерация археологов (500 записей)"""
    first_names = ['Иван', 'Петр', 'Сергей', 'Алексей', 'Дмитрий', 'Михаил', 'Андрей',
                   'Мария', 'Анна', 'Елена', 'Ольга', 'Татьяна', 'Наталья', 'Ирина']
    last_names = ['Иванов', 'Петров', 'Сидоров', 'Козлов', 'Николаев', 'Морозов',
                  'Волков', 'Алексеев', 'Лебедев', 'Семенов', 'Павлов', 'Федоров']

    values = []
    batch_size = 100

    for i in range(1, count + 1):
        first = random.choice(first_names)
        last = random.choice(last_names)
        spec_id = random.randint(1, 15)
        qual_id = random.randint(1, 15)
        salary = round(random.uniform(45000, 150000), 2)
        loc_id = random.randint(1, 15)
        hire_date = (datetime.now() - timedelta(days=random.randint(0, 5000))).strftime('%Y-%m-%d')

        values.append(f"({i}, '{first}', '{last}', {spec_id}, {qual_id}, {salary}, {loc_id}, '{hire_date}')")

        if i % batch_size == 0 or i == count:
            query = f"INSERT INTO archaeologists (id, first_name, last_name, specialization_id, qualification_id, salary, location_id, hire_date) VALUES {','.join(values)}"
            plpy.execute(query)
            values = []
            plpy.info(f"Generated {i}/{count} archaeologists")

def generate_finds(count=50000):
    """Генерация находок (50 000 записей)"""
    find_names = [
        'Золотая маска', 'Мраморная статуя', 'Бронзовый шлем', 'Королевская печать',
        'Ритуальный меч', 'Золотые монеты', 'Алебастровая ваза', 'Терракотовая фигура',
        'Каменный алтарь', 'Храмовая утварь', 'Императорский скипетр', 'Золотой кубок',
        'Глиняная табличка', 'Погребальная маска', 'Мраморный рельеф', 'Бронзовое зеркало',
        'Серебряная фибула', 'Янтарный амулет', 'Железный топор', 'Керамический сосуд'
    ]

    values = []
    batch_size = 5000

    for i in range(1, count + 1):
        name = random.choice(find_names) + ' ' + str(i)
        discovery_date = (datetime.now() - timedelta(days=random.randint(0, 5000))).strftime('%Y-%m-%d')
        archaeologist_id = random.randint(1, 500)
        epoch_id = random.randint(1, 15)
        owner_id = random.randint(1, 15)
        location_id = random.randint(1, 15)

        values.append(f"({i}, '{name}', '{discovery_date}', {archaeologist_id}, {epoch_id}, {owner_id}, {location_id})")

        if i % batch_size == 0 or i == count:
            query = f"INSERT INTO finds (id, name, discovery_date, archaeologist_id, epoch_id, owner_id, location_id) VALUES {','.join(values)}"
            plpy.execute(query)
            values = []
            plpy.info(f"Generated {i}/{count} finds")

def generate_artifacts(count=10000000):
    """Генерация 10 млн артефактов"""
    materials = ['золото', 'серебро', 'бронза', 'железо', 'медь', 'керамика', 'камень', 'мрамор', 'дерево', 'кость', 'стекло', 'янтарь']
    words = ['древний', 'античный', 'средневековый', 'ритуальный', 'уникальный', 'редкий', 'ценный', 'украшенный']

    values = []
    batch_size = 20000
    start_time = time.time()

    for i in range(1, count + 1):
        find_id = random.randint(1, 50000)
        type_id = random.randint(1, 15)
        cond_id = random.randint(1, 10)
        value = round(random.uniform(1000, 2500000), 2)
        material = random.choice(materials)
        description = random.choice(words) + ' ' + random.choice(words) + ' артефакт.'
        weight = round(random.uniform(10, 5000), 2)
        height = round(random.uniform(5, 200), 2)
        created_at = (datetime.now() - timedelta(days=random.randint(0, 1825))).strftime('%Y-%m-%d')

        values.append(f"({i}, {find_id}, {type_id}, {cond_id}, {value}, '{description}', '{created_at}', '{material}', {weight}, {height}, NULL, NULL)")

        if i % batch_size == 0 or i == count:
            query = f"INSERT INTO artifacts (id, find_id, type_id, condition_id, value, description, created_at, material, weight_grams, height_cm, width_cm, depth_cm) VALUES {','.join(values)}"
            plpy.execute(query)
            values = []

            elapsed = time.time() - start_time
            speed = i / elapsed if elapsed > 0 else 0
            if i % 100000 == 0:
                plpy.info(f"Generated {i}/{count} artifacts. Speed: {int(speed)} rows/sec")

    return time.time() - start_time

# Главная функция для археологии
try:
    start_time = time.time()
    plpy.info("=" * 50)
    plpy.info("НАЧАЛО ГЕНЕРАЦИИ АРХЕОЛОГИЧЕСКОЙ БАЗЫ")
    plpy.info("=" * 50)

    plpy.info("1. Генерация археологов (500 записей)...")
    generate_archaeologists(500)

    plpy.info("2. Генерация находок (50 000 записей)...")
    generate_finds(50000)

    plpy.info("3. Генерация артефактов (10 000 000 записей)...")
    elapsed = generate_artifacts(10000000)

    total_time = time.time() - start_time
    plpy.info("=" * 50)
    plpy.info(f"ГЕНЕРАЦИЯ АРХЕОЛОГИЧЕСКОЙ БАЗЫ ЗАВЕРШЕНА ЗА {total_time:.2f} СЕК")
    plpy.info("=" * 50)

    return f"SUCCESS! Археологическая база готова. Время: {total_time:.2f} сек"

except Exception as e:
    return f"ERROR: {str(e)}\n{traceback.format_exc()}"

$$ LANGUAGE plpython3u;

-- ============================================
-- ИНДЕКСЫ ДЛЯ АРХЕОЛОГИЧЕСКОЙ БАЗЫ
-- ============================================

CREATE INDEX idx_artifacts_find ON artifacts(find_id);
CREATE INDEX idx_artifacts_type ON artifacts(type_id);
CREATE INDEX idx_artifacts_condition ON artifacts(condition_id);
CREATE INDEX idx_artifacts_value ON artifacts(value);
CREATE INDEX idx_artifacts_created ON artifacts(created_at);
CREATE INDEX idx_artifacts_material ON artifacts(material);
CREATE INDEX idx_artifacts_description_trgm ON artifacts USING gin(description gin_trgm_ops);

CREATE INDEX idx_finds_archaeologist ON finds(archaeologist_id);
CREATE INDEX idx_finds_epoch ON finds(epoch_id);
CREATE INDEX idx_finds_owner ON finds(owner_id);
CREATE INDEX idx_finds_location ON finds(location_id);
CREATE INDEX idx_finds_date ON finds(discovery_date);

CREATE INDEX idx_archaeologists_specialization ON archaeologists(specialization_id);
CREATE INDEX idx_archaeologists_qualification ON archaeologists(qualification_id);
CREATE INDEX idx_archaeologists_location ON archaeologists(location_id);

-- ============================================
-- ЗАПУСК ГЕНЕРАЦИИ ДАННЫХ
-- ============================================

\c sql_tutor_university_db;
SELECT generate_university_data();

\c archaeology_10m;
SELECT generate_archaeology_data();

-- ============================================
-- НАСТРОЙКА РОЛЕЙ И ПРАВ ДОСТУПА
-- ============================================

\c postgres;

-- Создаем роли (если не существуют)
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'teacher_role') THEN
CREATE ROLE teacher_role WITH LOGIN PASSWORD 'teacher_pass' CREATEDB;
RAISE NOTICE 'Роль teacher_role создана';
ELSE
        RAISE NOTICE 'Роль teacher_role уже существует';
END IF;

    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'students') THEN
CREATE ROLE students WITH LOGIN PASSWORD 'student_pass';
RAISE NOTICE 'Роль students создана';
ELSE
        RAISE NOTICE 'Роль students уже существует';
END IF;
END
$$;

-- Даем права преподавателю на все базы
GRANT ALL PRIVILEGES ON DATABASE sql_tutor_university_db TO teacher_role;
GRANT ALL PRIVILEGES ON DATABASE archaeology_10m TO teacher_role;

-- Настраиваем права для студентов на каждой базе
\c sql_tutor_university_db;

GRANT CONNECT ON DATABASE sql_tutor_university_db TO students;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO students;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO students;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO students;

\c archaeology_10m;

GRANT CONNECT ON DATABASE archaeology_10m TO students;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO students;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO students;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO students;

-- Возвращаемся в postgres
\c postgres;

-- Устанавливаем ограничения для студентов
ALTER ROLE students SET statement_timeout = '30s';
ALTER ROLE students SET work_mem = '4MB';
ALTER ROLE students SET idle_in_transaction_session_timeout = '5min';
ALTER ROLE students CONNECTION LIMIT 20;

-- ============================================
-- ПРОВЕРКА РЕЗУЛЬТАТА
-- ============================================

SELECT '✅ Университетская база создана' as message;
\c sql_tutor_university_db;
SELECT count(*) as students_count FROM student;
SELECT count(*) as enrollments_count FROM enrollment;

SELECT '✅ Археологическая база создана' as message;
\c archaeology_10m;
SELECT count(*) as archaeologists_count FROM archaeologists;
SELECT count(*) as artifacts_count FROM artifacts;

\c postgres;
SELECT '✅ Роли настроены' as message;
\du

SELECT '✅ Готово! Теперь можно запускать приложение' as message;