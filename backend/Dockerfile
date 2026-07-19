FROM golang:1.25.1 AS base 
WORKDIR /app 
COPY go.mod .  
RUN go mod download
COPY . .

RUN go build -o app .
FROM gcr.io/distroless/base
COPY --from=base /app/app .

CMD ["./app"] 
