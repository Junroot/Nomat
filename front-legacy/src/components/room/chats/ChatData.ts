export interface ChatData {
  type: string;
  contents: Array<string>;
  photoUrl?: string | null;
  nickname?: string | null;
}
