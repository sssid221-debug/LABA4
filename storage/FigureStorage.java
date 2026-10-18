package storage;

import exceptions.FigureFormatException;
import geometry2d.Figure;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Хранилище списка фигур в файле. Реализации: текстовая и бинарная (сериализация). */
public interface FigureStorage {

    /**
     * Читает список фигур из файла.
     *
     * @param path путь к файлу
     * @return прочитанный список; для корректного файла без фигур — пустой список
     * @throws IOException            файл не найден, недоступен, повреждён
     * @throws FigureFormatException  содержимое файла не соответствует формату
     * @throws ClassNotFoundException при десериализации не найден класс объекта
     */
    List<Figure> read(Path path) throws IOException, FigureFormatException, ClassNotFoundException;

    /**
     * Записывает список фигур в файл, заменяя прежнее содержимое.
     *
     * @param path    путь к файлу
     * @param figures список фигур
     * @throws IOException           ошибка записи
     * @throws FigureFormatException фигуру нельзя представить в формате хранилища
     */
    void write(Path path, List<Figure> figures) throws IOException, FigureFormatException;
}
