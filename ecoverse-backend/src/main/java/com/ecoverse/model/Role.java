package com.ecoverse.model;

/**
 * User roles for authorization.
 *
 * USER:   Default role for all new accounts. Can access own resources.
 * SELLER: Can create and manage own products in the shop.
 * ADMIN:  Can access administrative operations.
 *
 * Security rules:
 * - New users default to USER
 * - No public endpoint can change a user's role
 * - Role promotion can only happen server-side by an existing ADMIN
 * - The first ADMIN is bootstrapped via secure startup mechanism (B2)
 */
public enum Role {
    USER,
    SELLER,
    ADMIN
}
