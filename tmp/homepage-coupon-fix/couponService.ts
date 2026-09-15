import { ApiError, apiClient, tokenManager } from '../../../services/api/apiClient';

export type CouponType = 'FLAT' | 'PERCENTAGE';
export type CouponAudience = 'PUBLIC' | 'PRIVATE_CODE' | 'ASSIGNED_USERS';

export interface CustomerCoupon {
  code: string; description?: string; type: CouponType; discountValue: number;
  minOrderValue: number; maxDiscountAmount?: number | null; validFrom: string;
  validUntil?: string | null; audience: CouponAudience; firstOrderOnly: boolean;
  showOnHomepage: boolean; remainingUses: number;
}

export const couponService = {
  authenticated: () => tokenManager.isAuthenticated(),
  available: () => apiClient.get<CustomerCoupon[]>('/customer/coupons'),
  async homepage(): Promise<CustomerCoupon | null> {
    if (tokenManager.isAuthenticated()) {
      try { return await apiClient.get<CustomerCoupon>('/customer/coupons/homepage'); }
      catch (error) {
        // A signed-in customer who is no longer eligible must not see the offer.
        if (error instanceof ApiError && error.status === 404) return null;
        // An expired token is cleared by apiClient; continue with the visitor offer.
        if (!(error instanceof ApiError) || error.status !== 401) throw error;
      }
    }
    try { return await apiClient.get<CustomerCoupon>('/coupons/homepage', { includeAuth: false }); }
    catch (error) { if (error instanceof ApiError && error.status === 404) return null; throw error; }
  },
};

export function couponOffer(coupon: CustomerCoupon): string {
  return coupon.type === 'FLAT'
    ? `Flat ₹${Number(coupon.discountValue).toLocaleString('en-IN')} off`
    : `${Number(coupon.discountValue)}% off${coupon.maxDiscountAmount ? ` up to ₹${Math.round(coupon.maxDiscountAmount / 100).toLocaleString('en-IN')}` : ''}`;
}
