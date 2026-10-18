package geometry3d;

import exceptions.InvalidFigureException;
import exceptions.Validation;
import geometry2d.Figure;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Цилиндр: произвольное плоское основание ({@link Figure}) и высота. Неизменяемый. */
public final class Cylinder implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Figure — интерфейс без Serializable; на практике основание — Circle или Rectangle,
    // оба сериализуемы. Несериализуемое основание вызовет NotSerializableException при записи.
    @SuppressWarnings("serial")
    private final Figure base;
    private final double height;

    /**
     * @param base   основание, не {@code null}
     * @param height высота: положительное конечное число
     * @throws InvalidFigureException если основание {@code null} или высота недопустима
     */
    public Cylinder(Figure base, double height) {
        this.base = Validation.requireNonNull("base", base);
        this.height = Validation.requirePositiveFinite("height", height);
    }

    public Figure getBase() {
        return base;
    }

    public double getHeight() {
        return height;
    }

    /** Объём = площадь основания (через интерфейс {@link Figure}) × высота. */
    public double volume() {
        return base.area() * height;
    }

    @Override
    public String toString() {
        return "Cylinder(base=" + base + ", height=" + height + ")";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Cylinder other
                && base.equals(other.base)
                && Double.compare(height, other.height) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(base, height);
    }

    /** Повторная проверка полей: при десериализации конструктор не вызывается. */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        try {
            Validation.requireNonNull("base", base);
            Validation.requirePositiveFinite("height", height);
        } catch (InvalidFigureException e) {
            InvalidObjectException invalid = new InvalidObjectException(e.getMessage());
            invalid.initCause(e);
            throw invalid;
        }
    }
}
