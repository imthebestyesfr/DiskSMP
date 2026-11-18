# DiskSMP

A starter Paper plugin project for the DiskSMP disc progression concept.

## Requirements
- Paper 26.1.x
- Java 25
- GitHub Actions builds the JAR automatically.

## Build on GitHub
1. Create a new GitHub repository.
2. Upload the contents of this folder (not the outer ZIP folder).
3. Open **Actions** and run **Build DiskSMP**, or push to `main`.
4. Open the completed workflow run and download the `DiskSMP` artifact.
5. Put `DiskSMP-1.0.0.jar` in the server's `plugins` folder and restart.

## Commands
- `/disksmp start`
- `/disksmp stop`
- `/disksmp center set`
- `/disksmp border set <diameter>`
- `/disksmp border expand <amount>`
- `/disksmp invsee <player>`
- `/disksmp stats [online-player]`
- `/disksmp reload`

All commands require `disksmp.admin` (default: OP).

## Current implementation notes
- A marked PAPER-style placeholder item is used as the progression disc. The uploaded ring-shaped disc design needs a matching resource pack/model before it can look exactly like the reference.
- The plugin tracks player kills and evolves the item using the requested cumulative requirements: 1, 2, 4, 6, 8, 12.
- Wooden has no enchantments. Loyalty is not applied to any tier.
- Disc levels currently use a configurable default of 5 additional kills per level. Change `kills-per-level` and `levels` in `config.yml` to tune this.
- The plugin currently uses player kills only; mob kills do not count.
- Start/stop pauses or resumes progression credit; it does not shut down the Minecraft server.
- This is an initial buildable foundation. Native throwing/returning physics, custom 3D ring models, full resource-pack delivery, and refined level XP rules are not implemented yet.
