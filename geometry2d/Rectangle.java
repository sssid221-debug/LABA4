package geometry2d;

import exceptions.InvalidFigureException;
import exceptions.Validation;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;

/** Прямоугольник. Неизменяемый: стороны задаются только в конструкторе. */
public final class Rectangle implements Figure, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final double sideA;
    private final double sideB;

    /**
     * @param sideA первая сторона: положительное конечное число
     * @param sideB вторая сторона: положительное конечное число
     * @throws InvalidFigureException если сторона недопустима
     */
    public Rectangle(double sideA, double sideB) {
        this.sideA = Validation.requirePositiveFinite("sideA", sideA);
        this.sideB = Validation.requirePositiveFinite("sideB", sideB);
    }

    public double getSideA() {
        return sideA;
    }

    public double getSideB() {
        return sideB;
    }

    @Override
    public double area() {
        return sideA * sideB;
    }

    @Override
    public double perimeter() {
        return 2 * (sideA + sideB);
    }

    @Override
    public String toString() {
        return "Rectangle(sideA=" + sideA + ", sideB=" + sideB + ")";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Rectangle other
                && Double.compare(sideA, other.sideA) == 0
                && Double.compare(sideB, other.sideB) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * Double.hashCode(sideA) + Double.hashCode(sideB);
    }

    /** Повторная проверка полей: при десериализации конструктор не вызывается. */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        try {
            Validation.requirePositiveFinite("sideA", sideA);
            Validation.requirePositiveFinite("sideB", sideB);
        } catch (InvalidFigureException e) {
            InvalidObjectException invalid = new InvalidObjectException(e.getMessage());
            invalid.initCause(e);
            throw invalid;
        }
    }
}
