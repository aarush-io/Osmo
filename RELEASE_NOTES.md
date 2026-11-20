Osmo 1.8.5

### Player
- Settings, Appearance has a new option, "Player blur style". It sets the style of the blur below the album art. There are three styles:
  - Default: the colours of the cover continue below it. This is the style from before.
  - Legacy: the moving colour shapes from v1.5.
  - Simple: a soft blur of the cover behind all of the player, the same as on the lyrics and queue pages.
- "Player blur style" replaces the "Legacy mesh gradient" switch. If that switch was on, the app starts with Legacy.
- New setting: Settings, Performance, Bluetooth video delay. When the sound goes to Bluetooth, the music video picture waits for the sound. Bluetooth sound comes late, and before this change the picture moved before the sound.
- This setting is a manual slider (0 to 500 ms). The default is 150 ms. Android cannot measure the full delay of a Bluetooth headset, so adjust it to match your headset. Set 0 to stop the delay.
- The delay applies to music videos, music videos used as a Canvas, and podcast episode videos. Spotify Canvas loops do not change.
- When you touch the audio quality line at the bottom of the player, the text becomes smaller and then springs back. The box that showed on the text before is removed.

### Home
- After you scroll past the dials, the category chips do not cover the first heading of the feed. The feed stops below the chips.

### Podcasts
- The Podcasts dial tiles do not change places when the feed loads. A show that is on the dial keeps its place. Only a new show goes into the place of a show that is not on the dial now.
- Podcasts that you add to YouTube Music from an RSS feed (Library, Add podcast, Add RSS feed) now play. The sound comes from the host of the podcast feed. You must be signed in.
- You cannot download an episode from an RSS feed podcast yet.

### Lyrics
- Credit lines at the start of the lyrics ("Lyrics by", "Composed by", "Composer:" and similar) are removed. A header line with the song name and artist is removed too. Lines after the first lyric line do not change.
- During a song intro, the lyric strip shows a progress bar until the first line. The bar slides up into the first line.
- When you open lyrics or the queue, they fade in while the album art becomes smaller. Before, they came in only after the art stopped.

### Other
- The dial layout now uses shared code. Your dials and pins do not change.
