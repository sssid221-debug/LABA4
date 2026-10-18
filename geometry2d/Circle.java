package geometry2d;

import exceptions.InvalidFigureException;
import exceptions.Validation;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;

/** Круг. Неизменяемый: радиус задаётся только в конструкторе. */
public final class Circle implements Figure, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final double radius;

    /**
     * @param radius радиус: положительное конечное число
     * @throws InvalidFigureException если радиус недопустим
     */
    public Circle(double radius) {
        this.radius = Validation.requirePositiveFinite("radius", radius);
    }

    public double getRadius() {
        return radius;
    }

    @Override
    public double area() {
        return Math.PI * radius * radius;
    }

    @Override
    public double perimeter() {
        return 2 * Math.PI * radius;
    }

    @Override
    public String toString() {
        return "Circle(radius=" + radius + ")";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Circle other && Double.compare(radius, other.radius) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(radius);
    }

    /**
     * При десериализации конструктор не вызывается, поэтому проверяем поля заново:
     * иначе подделанный бинарный файл создал бы круг с отрицательным радиусом.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        try {
            Validation.requirePositiveFinite("radius", radius);
        } catch (InvalidFigureException e) {
            InvalidObjectException invalid = new InvalidObjectException(e.getMessage());
            invalid.initCause(e);
            throw invalid;
        }
    }
}
