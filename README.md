# pangolin
## a render manager for people who hate render managers. (and paying)

i built this because i got tired of waiting for zips and digging through messy (or non existant) logs. pangolin is a ground-up rewrite of everything i learned while building previous tools. it's fast, it's stateless, and it's built for artists who just want their frames.

to start pangolin:
copy the .env.example, edit to your needs, rename to .env
run ```docker compose up -d --build``` in the root folder
done

note on security: pangolin has no built-in auth or https. it's designed to live in your local network/vlan. if you want to expose it to the web, put it behind a reverse proxy. i kept the core clean so you can scale it how you want.
