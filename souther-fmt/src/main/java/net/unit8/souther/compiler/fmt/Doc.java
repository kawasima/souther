package net.unit8.souther.compiler.fmt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * A Wadler/Leijen pretty-printing document. A {@link #group} is laid out flat (its {@link #line}s
 * become spaces) when it fits the target width, or broken (its lines become newlines at the current
 * indent) otherwise. This is what lets the formatter keep a short record or pipeline on one line and
 * break a long one, from a single description of its shape.
 */
public sealed interface Doc {

    Doc NIL = new Nil();
    Doc LINE = new Line(" ");        // a space when flat, a newline when broken
    Doc SOFTLINE = new Line("");     // nothing when flat, a newline when broken
    Doc HARDLINE = new Hard();       // always a newline; forces the enclosing group to break

    record Nil() implements Doc {}
    record Text(String s) implements Doc {}
    record Line(String flat) implements Doc {}
    record Hard() implements Doc {}
    record Concat(List<Doc> parts) implements Doc {}
    record Nest(int indent, Doc doc) implements Doc {}
    record Group(Doc doc) implements Doc {}

    static Doc text(String s) {
        return new Text(s);
    }

    static Doc concat(Doc... parts) {
        return new Concat(List.of(parts));
    }

    static Doc concat(List<Doc> parts) {
        return new Concat(List.copyOf(parts));
    }

    static Doc nest(int indent, Doc doc) {
        return new Nest(indent, doc);
    }

    static Doc group(Doc doc) {
        return new Group(doc);
    }

    /** Joins {@code parts} with {@code sep} between each. */
    static Doc join(Doc sep, List<Doc> parts) {
        List<Doc> out = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.add(sep);
            }
            out.add(parts.get(i));
        }
        return new Concat(out);
    }

    /** Renders this document to a string at the given target {@code width} (columns), using
     * {@code indentUnit} spaces per nesting level increment already folded into nest amounts. */
    default String render(int width) {
        StringBuilder sb = new StringBuilder();
        Deque<Item> todo = new ArrayDeque<>();
        todo.push(new Item(0, Mode.BREAK, this));   // the outermost context breaks
        int col = 0;
        while (!todo.isEmpty()) {
            Item it = todo.pop();
            switch (it.doc) {
                case Nil ignored -> { }
                case Text t -> {
                    sb.append(t.s());
                    col += t.s().length();
                }
                case Concat c -> {
                    List<Doc> parts = c.parts();
                    for (int i = parts.size() - 1; i >= 0; i--) {
                        todo.push(new Item(it.indent, it.mode, parts.get(i)));
                    }
                }
                case Nest n -> todo.push(new Item(it.indent + n.indent(), it.mode, n.doc()));
                case Group g -> {
                    Mode mode = fits(width - col, new Item(it.indent, Mode.FLAT, g.doc()), todo)
                            ? Mode.FLAT : Mode.BREAK;
                    todo.push(new Item(it.indent, mode, g.doc()));
                }
                case Line l -> {
                    if (it.mode == Mode.FLAT) {
                        sb.append(l.flat());
                        col += l.flat().length();
                    } else {
                        sb.append('\n').append(" ".repeat(it.indent));
                        col = it.indent;
                    }
                }
                case Hard ignored -> {
                    sb.append('\n').append(" ".repeat(it.indent));
                    col = it.indent;
                }
            }
        }
        return sb.toString();
    }

    /** Whether the documents starting with {@code first} then the queued rest fit in {@code remaining}
     * columns before the next forced break — the standard flat-fits check. */
    private static boolean fits(int remaining, Item first, Deque<Item> rest) {
        if (remaining < 0) {
            return false;
        }
        Deque<Item> todo = new ArrayDeque<>();
        todo.push(first);
        Iterator<Item> restIt = rest.iterator();
        while (true) {
            Item it;
            if (!todo.isEmpty()) {
                it = todo.pop();
            } else if (restIt.hasNext()) {
                it = restIt.next();
            } else {
                return true;
            }
            switch (it.doc) {
                case Nil ignored -> { }
                case Text t -> {
                    remaining -= t.s().length();
                    if (remaining < 0) {
                        return false;
                    }
                }
                case Concat c -> {
                    List<Doc> parts = c.parts();
                    for (int i = parts.size() - 1; i >= 0; i--) {
                        todo.push(new Item(it.indent, it.mode, parts.get(i)));
                    }
                }
                case Nest n -> todo.push(new Item(it.indent + n.indent(), it.mode, n.doc()));
                case Group g -> todo.push(new Item(it.indent, it.mode, g.doc()));
                case Line l -> {
                    if (it.mode == Mode.FLAT) {
                        remaining -= l.flat().length();
                        if (remaining < 0) {
                            return false;
                        }
                    } else {
                        return true;   // a break within reach means it fits
                    }
                }
                case Hard ignored -> {
                    // a hardline cannot be laid out flat: inside the group under test (FLAT) it forces
                    // a break; in an already-broken outer context it just ends the measured line.
                    return it.mode != Mode.FLAT;
                }
            }
        }
    }

    enum Mode { FLAT, BREAK }

    record Item(int indent, Mode mode, Doc doc) {}
}
