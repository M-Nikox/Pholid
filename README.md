# Pangolin
## A cool render manager for cool people!

I built this for fun, it's a full package of software for a functional render manager, using Flamenco, Blender and Docker for the rendering. It's fast, it's stateless, and it's built for artists who just want their frames packaged neatly in a little .zip.

To start pangolin:
Copy the .env.example, edit to your needs, rename to .env
Run ```docker compose up -d --build``` in the root folder, and you're done! Simply wait for Grafana to generate the .db first.

Important note on security: Pangolin has no built-in auth or https. It's designed to live in your local network/vlan. If you want to expose it to the web, put it behind a reverse proxy. I kept the core clean so you can scale it how you want.
When I say there's no security, I mean it, Grafana uses the .env for setting up an admin account, overall it's not really perfect, but if it lives on a local network/VLAN as I said, then it should be good!

I might make a branch that includes auth in the near future, but I'm unsure. I tend to only maintain projects that absolutely must be fixed due to critical bugs or errors.
