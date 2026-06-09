<!--suppress HtmlDeprecatedAttribute -->
<div align="center">
   <img width="512" height="512" alt="ArdaMaps_512px" src="https://github.com/user-attachments/assets/1e33547e-68a0-4515-8c15-0a675f7a9d63" />
</div>

# ArdaMaps

![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-62b47a?style=flat-square)
![Fabric](https://img.shields.io/badge/Fabric-loader-dbb66e?style=flat-square)
[![License: MIT](https://img.shields.io/badge/License-MIT-lightgrey?style=flat-square)](LICENSE)
[![License: CC BY 4.0](https://img.shields.io/badge/License-CC%20BY%204.0-lightgrey?style=flat-square)](https://creativecommons.org/licenses/by/4.0/)

A mapping mod for Minecraft servers. Adds an interactive map, compass and toposcope HUDs, fog-of-war exploration tracking, and an in-game guide book.

## Features

- Interactive map with PMTiles, BlueMap and flat images layer support and configurable zoom levels
- Compass and toposcope HUDs showing nearby landmarks with distance
- Fog-of-war exploration tracking stored per dimension
- In-game guide book, customizable via resource pack
- Location and POI data source configurable via API

## Requirements

**Required**
- Minecraft 1.20.1
- Fabric Loader ≥ 0.16.10
- Fabric API
- HuskHomes 4.7+ - (warp integration)

**Optional**
- Arda Regions - enables region data in the map

## Quick Setup

1. Install the mod jar on both the **server** and every **client**.
2. Start the server once - `config/arda-maps/server.json` is created automatically.
3. Edit `server.json`: set your API URLs and define at least one dimension entry.  
   See the [Server Configuration](https://github.com/Paul-Bantz/ArdaMaps/wiki/Server-Configuration) wiki page for all fields.
4. Client configuration is auto-generated on each player's first login.  
   See the [Client Configuration](https://github.com/Paul-Bantz/ArdaMaps/wiki/Client-Configuration) wiki page for all fields.
5. Server config is reloaded on each server start. To reload without restarting, run:
   ```
   /ardamaps refresh configuration
   ```

## Guide Book

The in-game guide book content is driven by HTML fragments and a JSON table of contents, and can be fully replaced by a resource pack. See the [Guide Setup](https://github.com/Paul-Bantz/ArdaMaps/wiki/Guide-Setup) wiki page for details.

## License
- Code is licensed under [MIT](LICENSE).
- ArdaMaps media is released under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
- BlueMap Tiles fragment shader code is ported from the original BlueMap project, licensed under MIT. See [Bluemap/LICENCE](https://github.com/BlueMap-Minecraft/BlueMap/blob/master/LICENSE) for details.

