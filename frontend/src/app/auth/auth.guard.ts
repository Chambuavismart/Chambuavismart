import { CanActivateFn } from '@angular/router';

// Login disabled: allow all routes without checks
export const authGuard: CanActivateFn = () => {
  return true;
};
