# 0004. Pairing with a confirmation code

Status: Accepted
Date: 2026-10-23

## Context

Signing in on a TV with a remote is painful. You sign in to AniList on the phone,
and the companion sends the account to the TV over the local network. There's no
hosted server in between.

The legacy app had sound encryption: ECDH on P-256, keys derived per direction
with HMAC-SHA256, then AES-GCM. It had two ways to find the TV. Scanning a QR
code was safe, because the QR carries the TV's public key. Typing the TV's
6-character code was not. The phone looked the code up on the network and
trusted whatever public key was advertised under it, so a device on the same
Wi-Fi could pretend to be the TV and receive the token.

## Decision

- Keep the legacy encryption design and move it into `:shared`, with tests.
- QR pairing stays as it is.
- Code-entry pairing adds a confirmation step. Once connected, both phone and TV
  show a short code derived from the shared secret, and the user confirms they
  match before any account data is sent.
- The TV accepts several connection attempts while pairing is open, with short
  timeouts. A bad or idle connection no longer ends the session.
- Local network only. No hosted pairing service.

## Consequences

- Code-entry pairing becomes safe against someone else on the network, at the
  cost of one extra tap.
- The protocol is documented and tested in `:shared`, so an iPhone companion
  could implement it later.
