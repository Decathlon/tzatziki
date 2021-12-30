package edu.utexas.tacc;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.stream.Stream;

/**
 * Handlebars Math Helper
 *
 * <p>
 * Perform math operations in handlebars. Inspired by: http://jsfiddle.net/mpetrovich/wMmHS/
 * Operands are treated as java.math.BigDecimal and operations are performed with the
 * java.math.MathContext.DECIMAL64 MathContext, yielding results with 16 bits of precision
 * and rounding to the nearest EVEN digit, according to IEEE 754R. You can force rounding
 * decimal results using the extra parameter <code>scale</code>, which corresponds to calling
 * <code>BigDecimal.setScale(int scale, RoundingMode.HALF_UP)</code>.
 * </p>
 *
 * <p>addition</p>
 *
 * <pre>{{math arg0 "+" arg1}} // arg0 + arg1</pre>
 *
 * <p>subtraction</p>
 *
 * <pre>{{math arg0 "-" arg1}} // arg0 - arg1</pre>
 *
 * <p>multiplication</p>
 *
 * <pre>{{math arg0 "*" arg1}} // arg0 * arg1</pre>
 *
 * <p>division</p>
 *
 * <pre>{{math arg0 "/" arg1}} // arg0 / arg1</pre>
 *
 * <p>modulus</p>
 *
 * <pre>{{math arg0 "%" arg1}} // arg0 % arg1</pre>
 *
 * @author mrhanlon
 * @see java.math.BigDecimal
 * @see java.math.MathContext
 */
public class MathHelper implements Helper<Object> {

    public enum Operator {
        add("+"),
        subtract("-"),
        multiply("*"),
        divide("/"),
        mod("%");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        public static Operator fromSymbol(String symbol) {
            return Stream.of(values()).filter(value -> value.symbol.equals(symbol)).findFirst().orElse(null);
        }
    }

    @Override
    public CharSequence apply(final Object value, Options options) throws IOException, IllegalArgumentException {
        if (options.params.length >= 2) {
            Operator op = Operator.fromSymbol(options.param(0).toString());
            if (op == null) {
                throw new IllegalArgumentException("Unknown operation '" + options.param(0) + "'");
            }

            Integer scale = options.hash("scale");

            MathContext mc = new MathContext(16, RoundingMode.HALF_UP);

            if (value == null || options.param(1) == null) {
                throw new IllegalArgumentException("Cannot perform operations on null values");
            }

            BigDecimal t0 = new BigDecimal(value.toString());
            BigDecimal t1 = new BigDecimal(options.param(1).toString());
            BigDecimal result = switch (op) {
                case add -> t0.add(t1, mc);
                case subtract -> t0.subtract(t1, mc);
                case multiply -> t0.multiply(t1, mc);
                case divide -> t0.divide(t1, mc);
                case mod -> t0.remainder(t1, mc);
            };
            if (scale != null) {
                result = result.setScale(scale, RoundingMode.HALF_UP);
            }
            return result.toString();
        } else {
            throw new IOException("MathHelper requires three parameters");
        }
    }
}
