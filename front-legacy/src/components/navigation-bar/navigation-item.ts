import type { ComponentType } from "svelte";

export interface NavigationItemData {
  iconComponent: ComponentType;
  label: string;
  onClick?: () => void | null;
}
