package io.github.luyingyilong.ore.gui;

import io.github.luyingyilong.ore.control.data.Table;

import java.util.Objects;

/**
 * Explicit Java-provided table cell, including retained ControlCell implementations.
 */
public record UiTableCell(Table.Cell<UiTableRow> cell) {
    public UiTableCell {
        Objects.requireNonNull(cell);
    }

    public Table.Cell<UiTableRow> getCell() {
        return cell;
    }
}
