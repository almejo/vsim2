package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.logic.Expression.And;
import cl.almejo.vsim.core.logic.Expression.Constant;
import cl.almejo.vsim.core.logic.Expression.Not;
import cl.almejo.vsim.core.logic.Expression.Or;
import cl.almejo.vsim.core.logic.Expression.Variable;
import cl.almejo.vsim.core.logic.Expression.Xor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Random expression trees for tests, flattened the way the parser builds them (no And directly inside an And, …),
 * with constants, double NOTs, XOR chains and operations of up to six operands.
 */
final class RandomExpressions {

    private RandomExpressions() {
    }

    static Expression of(Random random, List<String> names, int depth) {
        int choice = random.nextInt(depth <= 0 ? 3 : 10);
        if (choice == 0 && random.nextInt(4) == 0) {
            return new Constant(random.nextBoolean());
        }
        if (choice <= 2) {
            return new Variable(names.get(random.nextInt(names.size())));
        }
        if (choice == 3) {
            return new Not(of(random, names, depth - 1));
        }
        int operandCount = 2 + random.nextInt(random.nextInt(4) == 0 ? 5 : 2);
        List<Expression> operands = new ArrayList<>();
        int kind = choice % 3;
        for (int operand = 0; operand < operandCount; operand++) {
            Expression child = of(random, names, depth - 1);
            List<Expression> flattened = switch (child) {
                case And and when kind == 0 -> and.operands();
                case Or or when kind == 1 -> or.operands();
                case Xor xor when kind == 2 -> xor.operands();
                default -> List.of(child);
            };
            operands.addAll(flattened);
        }
        return switch (kind) {
            case 0 -> new And(operands);
            case 1 -> new Or(operands);
            default -> new Xor(operands);
        };
    }
}
