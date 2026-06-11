package com.stay.application.support

import com.stay.domain.wishlist.Wishlist
import com.stay.domain.wishlist.WishlistId
import com.stay.domain.wishlist.WishlistRepository

/**
 * WishlistRepository 인메모리 Fake — 복합 PK (WishlistId) 키 저장.
 */
class FakeWishlistRepository : WishlistRepository {

    private val store = mutableMapOf<WishlistId, Wishlist>()

    val count: Int
        get() = store.size

    override fun existsByUserIdAndPropertyId(userId: Long, propertyId: Long): Boolean =
        store.containsKey(WishlistId(userId, propertyId))

    override fun save(wishlist: Wishlist): Wishlist {
        store[wishlist.id] = wishlist
        return wishlist
    }

    override fun deleteByUserIdAndPropertyId(userId: Long, propertyId: Long) {
        store.remove(WishlistId(userId, propertyId))
    }
}
