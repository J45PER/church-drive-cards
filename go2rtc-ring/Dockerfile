# go2rtc-ring builds go2rtc from source on the Home Assistant device, with two
# patches applied to the GO2RTC_VERSION release, in this order:
#   1. go2rtc-hevc-ap.patch  upstream go2rtc PR #2296 (HEVC aggregation packets),
#      the same patch as the go2rtc-hevc-fix add-on this replaces.
#   2. go2rtc-ring.patch     Church Drive's Ring signalling fix (see DOCS.md).
#   3. go2rtc-ring-logging.patch  more Ring logging: peer state, why a stream stopped.
# A build only exists if all the patches apply and their packages' tests pass.
ARG BUILD_FROM

FROM golang:1.25-alpine AS build
ARG GO2RTC_VERSION=1.9.14
RUN apk add --no-cache git
WORKDIR /src
RUN git -c advice.detachedHead=false clone --depth 1 \
        --branch "v${GO2RTC_VERSION}" https://github.com/AlexxIT/go2rtc.git .
COPY go2rtc-hevc-ap.patch go2rtc-ring.patch go2rtc-ring-logging.patch /tmp/
RUN git apply /tmp/go2rtc-hevc-ap.patch \
    && git apply /tmp/go2rtc-ring.patch \
    && git apply /tmp/go2rtc-ring-logging.patch \
    && go test ./pkg/h265/ ./pkg/ring/ ./pkg/webrtc/ \
    && CGO_ENABLED=0 go build -trimpath -ldflags "-s -w" -o /go2rtc .

FROM ${BUILD_FROM}
# go2rtc spawns ffmpeg for any "ffmpeg:" source, which is how Home Assistant
# wraps RTSP cameras. The Home Assistant base image ships no ffmpeg, so install it.
RUN apk add --no-cache ffmpeg
COPY --from=build /go2rtc /usr/local/bin/go2rtc
COPY rootfs /
RUN chmod +x /etc/s6-overlay/s6-rc.d/go2rtc/run
