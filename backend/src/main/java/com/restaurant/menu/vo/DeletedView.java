package com.restaurant.menu.vo;

/** 逻辑删除结果（{@code {"deleted": true}}）。 */
public record DeletedView(boolean deleted) {

    public static DeletedView yes() {
        return new DeletedView(true);
    }
}
