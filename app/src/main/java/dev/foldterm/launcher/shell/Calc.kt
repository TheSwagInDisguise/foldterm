package dev.foldterm.launcher.shell

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.pow

/** Small recursive-descent calculator: + - * / % ^, parentheses, unary minus, pi, e. */
object Calc {
    private val looksLikeMath = Regex("^[\\d\\s.+\\-*/%^()xX×÷,]+$|^[\\d\\s.+\\-*/%^()xX×÷,]*(pi|e)[\\d\\s.+\\-*/%^()xX×÷,pie]*$")

    /** True when [s] is plausibly an expression worth showing a live result for. */
    fun isExpression(s: String): Boolean {
        val t = s.trim().removePrefix("=").trim()
        return t.isNotEmpty() && looksLikeMath.matches(t) && t.any { it.isDigit() } &&
            t.any { it in "+-*/%^×÷xX" } && !t.matches(Regex("^-?\\d+(\\.\\d+)?$"))
    }

    fun eval(s: String): Double {
        val p = Parser(s.trim().removePrefix("=").replace('×', '*').replace('÷', '/').replace('x', '*').replace('X', '*').replace(",", ""))
        val v = p.expr()
        p.skip()
        require(p.pos == p.src.length) { "unexpected '${p.src.getOrNull(p.pos)}'" }
        return v
    }

    fun format(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return v.toString()
        if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) return v.toLong().toString()
        return BigDecimal(v).round(MathContext(12)).stripTrailingZeros().toPlainString()
    }

    private class Parser(val src: String) {
        var pos = 0
        fun skip() { while (pos < src.length && src[pos] == ' ') pos++ }
        fun eat(c: Char): Boolean { skip(); return if (pos < src.length && src[pos] == c) { pos++; true } else false }

        fun expr(): Double {
            var v = term()
            while (true) v = when { eat('+') -> v + term(); eat('-') -> v - term(); else -> return v }
        }
        fun term(): Double {
            var v = factor()
            while (true) v = when {
                eat('*') -> v * factor()
                eat('/') -> { val d = factor(); require(d != 0.0) { "division by zero" }; v / d }
                eat('%') -> v % factor()
                else -> return v
            }
        }
        fun factor(): Double {
            val base = unary()
            return if (eat('^')) base.pow(factor()) else base
        }
        fun unary(): Double = when {
            eat('-') -> -unary()
            eat('+') -> unary()
            else -> atom()
        }
        fun atom(): Double {
            skip()
            if (eat('(')) { val v = expr(); require(eat(')')) { "missing )" }; return v }
            if (src.startsWith("pi", pos)) { pos += 2; return Math.PI }
            if (pos < src.length && src[pos] == 'e' && (pos + 1 >= src.length || !src[pos + 1].isLetter())) { pos++; return Math.E }
            val start = pos
            while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) pos++
            require(pos > start) { "expected a number" }
            return src.substring(start, pos).toDouble()
        }
    }
}
