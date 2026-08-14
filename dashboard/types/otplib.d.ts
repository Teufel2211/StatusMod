declare module "otplib" {
  export function generateSecret(): string
  export function verify(params: { token: string; secret: string }): boolean
}
