package com.example.ecommerce.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QCartItem is a Querydsl query type for CartItem
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QCartItem extends EntityPathBase<CartItem> {

    private static final long serialVersionUID = 224549886L;

    public static final QCartItem cartItem = new QCartItem("cartItem");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final NumberPath<Long> price = createNumber("price", Long.class);

    public final NumberPath<Long> productId = createNumber("productId", Long.class);

    public final StringPath productName = createString("productName");

    public final NumberPath<Integer> quantity = createNumber("quantity", Integer.class);

    public final StringPath username = createString("username");

    public QCartItem(String variable) {
        super(CartItem.class, forVariable(variable));
    }

    public QCartItem(Path<? extends CartItem> path) {
        super(path.getType(), path.getMetadata());
    }

    public QCartItem(PathMetadata metadata) {
        super(CartItem.class, metadata);
    }

}

