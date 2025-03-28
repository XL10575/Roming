import java.util.*;
import java.util.function.Function;

public final class MatrixMap<T> {

    public static class InvalidLengthException extends Exception {
        public enum Cause { ROW, COLUMN }
        private final Cause cause;
        private final int length;
        public InvalidLengthException(Cause cause, int length) {
            this.cause = cause;
            this.length = length;
        }
        public Cause getTheCause() { return cause; }
        public int getTheLength() { return length; }
        public static int requireNonEmpty(Cause cause, int length) {
            if (length <= 0) {
                throw new IllegalArgumentException(new InvalidLengthException(cause, length));
            }
            return length;
        }
    }

    /**
     * The underlying matrix data (a RoamingMap from Indexes to T)
     */
    private final RoamingMap<Indexes, T> matrix;

    private MatrixMap(RoamingMap<Indexes, T> matrix) {
        this.matrix = matrix;
    }

    /** 
     * Returns a MatrixMap with given rows and columns, using valueMapper to generate values.
     */
    public static <S> MatrixMap<S> instance(int rows, int columns, Function<Indexes, S> valueMapper) {
        Objects.requireNonNull(valueMapper);
        RoamingMap<Indexes, S> matrix = buildMatrix(rows, columns, valueMapper);
        return new MatrixMap<>(matrix);
    }

    /**
     * Returns a MatrixMap with size specified by the given Indexes (row count and column count).
     */
    public static <S> MatrixMap<S> instance(Indexes size, Function<Indexes, S> valueMapper) {
        Objects.requireNonNull(size);
        Objects.requireNonNull(valueMapper);
        RoamingMap<Indexes, S> matrix = buildMatrix(size.row(), size.column(), valueMapper);
        return new MatrixMap<>(matrix);
    }

    /**
     * Returns an N x N MatrixMap with all entries equal to the given value.
     */
    public static <S> MatrixMap<S> constant(int size, S value) {
        Objects.requireNonNull(value);
        return instance(size, size, indexes -> value);
    }

    /**
     * Returns an N x N identity MatrixMap: identity value on diagonal, zero value elsewhere.
     */
    public static <S> MatrixMap<S> identity(int size, S zero, S identity) {
        Objects.requireNonNull(zero);
        Objects.requireNonNull(identity);
        return instance(size, size, indexes -> (indexes.areDiagonal() ? identity : zero));
    }

    /**
     * Constructs a MatrixMap from a 2D array.
     * The resulting MatrixMap has size [matrix.length x matrix[0].length] with corresponding values.
     */
    public static <S> MatrixMap<S> from(S[][] matrix) {
        Objects.requireNonNull(matrix);
        int rows = InvalidLengthException.requireNonEmpty(InvalidLengthException.Cause.ROW, matrix.length);
        int columns = InvalidLengthException.requireNonEmpty(InvalidLengthException.Cause.COLUMN, matrix[0].length);
        RoamingMap<Indexes, S> mapData = buildMatrix(rows, columns, indexes -> indexes.value(matrix));
        return new MatrixMap<>(mapData);
    }

    /**
     * @return Indexes with row = number of rows, column = number of columns in this matrix
     */
    public Indexes size() {
        Iterator<Indexes> iterator = Barricade.correctKeySet(matrix).iterator();
        // Find the maximum index (largest row and column)
        Indexes maxIndex = iterator.hasNext() ? iterator.next() : Indexes.ORIGIN;
        while (iterator.hasNext()) {
            Indexes current = iterator.next();
            if (current.compareTo(maxIndex) > 0) {
                maxIndex = current;
            }
        }
        return new Indexes(maxIndex.row() + 1, maxIndex.column() + 1);
    }

    /**
     * @return a String representation of the matrix (uses Barricade to ensure correctness)
     */
    @Override
    public String toString() {
        return Barricade.correctStringRepresentation(matrix);
    }

    /**
     * Retrieves the value at the given matrix indexes.
     * @param indexes the index pair
     * @return the value at that position in the matrix
     */
    public T value(Indexes indexes) {
        Objects.requireNonNull(indexes);
        return Barricade.getWithStateVar(matrix, indexes).value();
    }

    /**
     * Convenience method to retrieve the value at (row, column).
     */
    public T value(int row, int column) {
        return value(new Indexes(row, column));
    }

    /**
     * Builds the internal matrix (RoamingMap from Indexes to S) of given dimensions using valueMapper.
     */
    private static <S> RoamingMap<Indexes, S> buildMatrix(int rows, int columns, Function<Indexes, S> valueMapper) {
        int rowsNumber = InvalidLengthException.requireNonEmpty(InvalidLengthException.Cause.ROW, rows);
        int columnsNumber = InvalidLengthException.requireNonEmpty(InvalidLengthException.Cause.COLUMN, columns);
        RoamingMap<Indexes, S> matrix = new RoamingMap<>();
        // Populate all indices from (0,0) to (rowsNumber-1, columnsNumber-1)
        Indexes.stream(rowsNumber - 1, columnsNumber - 1).forEach(indexes -> {
            S value = valueMapper.apply(indexes);
            Barricade.putWithStateVar(matrix, indexes, value);
        });
        return matrix;
    }
}
