import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import LoginPage from './LoginPage'
import { api } from '../api/api'

const { showAlertMock } = vi.hoisted(() => ({ showAlertMock: vi.fn() }))

vi.mock('../context/AlertContext', () => ({
  useAlert: () => ({ showAlert: showAlertMock }),
}))

vi.mock('../api/api', () => ({
  api: {
    login: vi.fn(),
    register: vi.fn(),
    verifyEmail: vi.fn(),
    resendCode: vi.fn(),
    forgotPassword: vi.fn(),
    resetPassword: vi.fn(),
    loginGoogle: vi.fn(),
  },
}))

function renderLoginPage() {
  const onLoginSuccess = vi.fn()
  const onBack = vi.fn()
  render(<LoginPage onLoginSuccess={onLoginSuccess} onBack={onBack} />)
  return { onLoginSuccess, onBack }
}

// The Login tab renders two buttons with the exact same accessible name "Iniciar Sesión": the
// login/register tab toggle (type="button") and the form's own submit button. Disambiguate by
// picking the submit one whenever we mean "actually submit the login form".
function getLoginSubmitButton() {
  const candidates = screen.getAllByRole('button', { name: 'Iniciar Sesión' })
  const submitBtn = candidates.find((b) => b.getAttribute('type') === 'submit')
  if (!submitBtn) throw new Error('Login submit button not found')
  return submitBtn
}

describe('LoginPage', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  afterEach(() => {
    localStorage.clear()
  })

  describe('Login tab', () => {
    it('submits valid credentials, stores the user and calls onLoginSuccess', async () => {
      const user = userEvent.setup()
      const fakeUser = { id: 1, nombre: 'Ana', rol: 'PACIENTE', email: 'ana@test.com' }
      ;(api.login as any).mockResolvedValueOnce(fakeUser)
      const { onLoginSuccess } = renderLoginPage()

      await user.type(screen.getByLabelText('Email'), 'Ana@Test.com')
      await user.type(screen.getByLabelText('Contraseña'), 'Sup3rSecret')
      await user.click(getLoginSubmitButton())

      await waitFor(() => expect(onLoginSuccess).toHaveBeenCalledWith(fakeUser))
      expect(api.login).toHaveBeenCalledWith({ email: 'ana@test.com', password: 'Sup3rSecret' })
      expect(JSON.parse(localStorage.getItem('tranqui_user') || 'null')).toEqual(fakeUser)
    })

    it('shows an error alert on invalid credentials and does not call onLoginSuccess', async () => {
      const user = userEvent.setup()
      ;(api.login as any).mockRejectedValueOnce(new Error('Credenciales no válidas.'))
      const { onLoginSuccess } = renderLoginPage()

      await user.type(screen.getByLabelText('Email'), 'ana@test.com')
      await user.type(screen.getByLabelText('Contraseña'), 'wrongpass')
      await user.click(getLoginSubmitButton())

      await waitFor(() => expect(showAlertMock).toHaveBeenCalledWith('Credenciales no válidas.', 'error'))
      expect(onLoginSuccess).not.toHaveBeenCalled()
      expect(localStorage.getItem('tranqui_user')).toBeNull()
    })

    it('redirects to the verify-email tab when login fails because the account is unverified', async () => {
      const user = userEvent.setup()
      ;(api.login as any).mockRejectedValueOnce(new Error('Debés verificar tu correo antes de ingresar.'))
      renderLoginPage()

      await user.type(screen.getByLabelText('Email'), 'ana@test.com')
      await user.type(screen.getByLabelText('Contraseña'), 'somepass')
      await user.click(getLoginSubmitButton())

      await waitFor(() => expect(screen.getByLabelText('Código de Verificación')).toBeInTheDocument())
    })

    it('blocks submission with a warning alert when email or password is empty', async () => {
      const user = userEvent.setup()
      renderLoginPage()

      // Fill only the email so the native `required` attribute on the empty password
      // field doesn't itself block the submit event before handleFormLogin runs.
      await user.type(screen.getByLabelText('Email'), 'ana@test.com')
      const form = getLoginSubmitButton().closest('form')!
      // Remove native validation so we hit the JS-level guard in handleFormLogin.
      form.querySelectorAll('[required]').forEach((el) => el.removeAttribute('required'))
      await user.click(getLoginSubmitButton())

      expect(showAlertMock).toHaveBeenCalledWith('Por favor, completá todos los campos.', 'warning')
      expect(api.login).not.toHaveBeenCalled()
    })
  })

  describe('Tab toggle', () => {
    it('switches between the Login and Register forms', async () => {
      const user = userEvent.setup()
      renderLoginPage()

      expect(screen.getByLabelText('Email')).toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: 'Registrarse' }))
      expect(screen.getByText('Paso 1 de 2')).toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: 'Iniciar Sesión' }))
      expect(screen.getByLabelText('Email')).toBeInTheDocument()
    })
  })

  describe('Register tab — step 1 validation', () => {
    async function fillStep1(user: ReturnType<typeof userEvent.setup>, { password, confirm }: { password: string; confirm: string }) {
      renderLoginPage()
      await user.click(screen.getByRole('button', { name: 'Registrarse' }))

      const emailInput = screen.getByPlaceholderText('ejemplo@correo.com')
      await user.type(emailInput, 'nuevo@test.com')
      const [passwordInput, confirmInput] = screen.getAllByPlaceholderText('********')
      await user.type(passwordInput, password)
      await user.type(confirmInput, confirm)
      await user.click(screen.getByRole('button', { name: 'Siguiente →' }))
    }

    it('blocks advancing to step 2 with a weak password, without calling the API', async () => {
      const user = userEvent.setup()
      await fillStep1(user, { password: 'abcdefgh', confirm: 'abcdefgh' })

      expect(showAlertMock).toHaveBeenCalledWith(
        'La contraseña debe incluir al menos una letra mayúscula.',
        'warning'
      )
      // Still on step 1 — the "Nombre" field from step 2 never appears.
      expect(screen.queryByText('Paso 2 de 2')).not.toBeInTheDocument()
      expect(api.register).not.toHaveBeenCalled()
    })

    it('blocks advancing when the passwords do not match', async () => {
      const user = userEvent.setup()
      await fillStep1(user, { password: 'Sup3rSecret', confirm: 'Sup3rSecretX' })

      expect(showAlertMock).toHaveBeenCalledWith('Las contraseñas no coinciden.', 'warning')
      expect(screen.queryByText('Paso 2 de 2')).not.toBeInTheDocument()
    })

    it('advances to step 2 with a strong, matching password', async () => {
      const user = userEvent.setup()
      await fillStep1(user, { password: 'Sup3rSecret1', confirm: 'Sup3rSecret1' })

      expect(screen.getByText('Paso 2 de 2')).toBeInTheDocument()
    })
  })

  describe('Register tab — patient happy path', () => {
    async function fillStep1AndAdvance(user: ReturnType<typeof userEvent.setup>, email: string) {
      await user.click(screen.getByRole('button', { name: 'Registrarse' }))
      await user.type(screen.getByPlaceholderText('ejemplo@correo.com'), email)
      const [passwordInput, confirmInput] = screen.getAllByPlaceholderText('********')
      await user.type(passwordInput, 'Sup3rSecret1')
      await user.type(confirmInput, 'Sup3rSecret1')
      await user.click(screen.getByRole('button', { name: 'Siguiente →' }))
      await screen.findByText('Paso 2 de 2')
    }

    it('registers a PACIENTE without obra social and moves to the verify-email tab', async () => {
      const user = userEvent.setup()
      ;(api.register as any).mockResolvedValueOnce({})
      renderLoginPage()

      await fillStep1AndAdvance(user, 'paciente@test.com')

      // Step 2: Nombre and Apellido are the first two type="text" inputs (the masked date
      // field and the documento-número field are also type="text", further down).
      const step2Form = screen.getByRole('button', { name: 'Finalizar Registro' }).closest('form')!
      const textInputs = step2Form.querySelectorAll('input[type="text"]')
      await user.type(textInputs[0] as HTMLInputElement, 'Juana')
      await user.type(textInputs[1] as HTMLInputElement, 'Pérez')
      await user.type(screen.getByPlaceholderText('DD/MM/AAAA'), '15081995')
      await user.type(screen.getByPlaceholderText('12345678'), '30111222')
      await user.type(screen.getByPlaceholderText('1112345678'), '3511234567')

      const acceptTerms = step2Form.querySelector('#acceptedTermsRegisterStep2') as HTMLInputElement
      await user.click(acceptTerms)

      await user.click(screen.getByRole('button', { name: 'Finalizar Registro' }))

      await waitFor(() => expect(api.register).toHaveBeenCalledTimes(1))
      const payload = (api.register as any).mock.calls[0][0]
      expect(payload).toMatchObject({
        email: 'paciente@test.com',
        rol: 'PACIENTE',
        nombre: 'Juana',
        apellido: 'Pérez',
        fechaNacimiento: '1995-08-15',
        numeroDocumento: 30111222,
        telefono: '+54 3511234567',
        obraSocial: null,
        numAfiliado: null,
      })

      expect(showAlertMock).toHaveBeenCalledWith(
        '¡Registro exitoso! Enviamos un código de 6 dígitos a tu correo para activar tu cuenta.',
        'success'
      )
      await waitFor(() => expect(screen.getByLabelText('Código de Verificación')).toBeInTheDocument())
    })

    it('requires obra social + número de afiliado when "Tengo Cobertura" is checked, then submits with them', async () => {
      const user = userEvent.setup()
      ;(api.register as any).mockResolvedValueOnce({})
      renderLoginPage()

      await fillStep1AndAdvance(user, 'conobra@test.com')

      const step2Form = screen.getByText('Nombre').closest('form')!
      const textInputs = step2Form.querySelectorAll('input[type="text"]')
      await user.type(textInputs[0] as HTMLInputElement, 'Carla')
      await user.type(textInputs[1] as HTMLInputElement, 'Díaz')
      await user.type(screen.getByPlaceholderText('DD/MM/AAAA'), '01011990')
      await user.type(screen.getByPlaceholderText('12345678'), '28999111')
      await user.type(screen.getByPlaceholderText('1112345678'), '3512223344')

      await user.click(screen.getByLabelText('Tengo Cobertura / Obra Social o Prepaga'))
      // With hasObraSocial checked, PACIENTE now needs a 3rd step — no terms checkbox here yet.
      await user.click(screen.getByRole('button', { name: 'Siguiente →' }))

      await screen.findByText('Paso 3 de 3')

      // Accept terms first — otherwise that check fires before the obra-social validation.
      const step3Form = screen.getByRole('button', { name: 'Finalizar Registro' }).closest('form')!
      const acceptTerms = step3Form.querySelector('#acceptedTermsRegisterStep3') as HTMLInputElement
      await user.click(acceptTerms)

      // The obra-social <select> and número-de-afiliado <input> are natively `required` and
      // still empty at this point — strip that so browser-level constraint validation doesn't
      // swallow the submit before handleFormRegister's own JS validation gets to run.
      step3Form.querySelectorAll('[required]').forEach((el) => el.removeAttribute('required'))

      // Submitting step 3 without picking an obra social / afiliado is blocked.
      await user.click(screen.getByRole('button', { name: 'Finalizar Registro' }))
      expect(showAlertMock).toHaveBeenCalledWith('Por favor, completá los datos de tu obra social.', 'warning')
      expect(api.register).not.toHaveBeenCalled()

      await user.selectOptions(screen.getByRole('combobox'), 'OSDE')
      await user.type(screen.getByPlaceholderText('Ej: 123456789/01'), '123456/01')

      await user.click(screen.getByRole('button', { name: 'Finalizar Registro' }))

      await waitFor(() => expect(api.register).toHaveBeenCalledTimes(1))
      const payload = (api.register as any).mock.calls[0][0]
      expect(payload).toMatchObject({ obraSocial: 'OSDE', numAfiliado: '123456/01' })
    })
  })

  describe('Forgot / reset password', () => {
    it('requests a recovery code and moves to the reset-password tab', async () => {
      const user = userEvent.setup()
      ;(api.forgotPassword as any).mockResolvedValueOnce({})
      renderLoginPage()

      await user.click(screen.getByText('¿Olvidaste tu contraseña?'))
      await user.type(screen.getByLabelText('Email Registrado'), 'ana@test.com')
      await user.click(screen.getByRole('button', { name: 'Enviar Código de Recuperación' }))

      await waitFor(() => expect(api.forgotPassword).toHaveBeenCalledWith({ email: 'ana@test.com' }))
      expect(showAlertMock).toHaveBeenCalledWith(
        'Si el correo está registrado, recibirás un código de 6 dígitos.',
        'success'
      )
      await waitFor(() => expect(screen.getByLabelText('Código de 6 dígitos')).toBeInTheDocument())
    })

    it('rejects a weak new password on the reset-password tab without calling the API', async () => {
      const user = userEvent.setup()
      ;(api.forgotPassword as any).mockResolvedValueOnce({})
      renderLoginPage()

      await user.click(screen.getByText('¿Olvidaste tu contraseña?'))
      await user.type(screen.getByLabelText('Email Registrado'), 'ana@test.com')
      await user.click(screen.getByRole('button', { name: 'Enviar Código de Recuperación' }))
      await screen.findByLabelText('Código de 6 dígitos')

      await user.type(screen.getByLabelText('Código de 6 dígitos'), '123456')
      const [newPasswordInput, confirmNewPasswordInput] = screen.getAllByPlaceholderText('********')
      await user.type(newPasswordInput, 'weak')
      await user.type(confirmNewPasswordInput, 'weak')
      await user.click(screen.getByRole('button', { name: 'Restablecer Contraseña' }))

      expect(showAlertMock).toHaveBeenCalledWith(
        'La contraseña debe tener al menos 8 caracteres.',
        'warning'
      )
      expect(api.resetPassword).not.toHaveBeenCalled()
    })

    it('resets the password successfully and returns to the login tab', async () => {
      const user = userEvent.setup()
      ;(api.forgotPassword as any).mockResolvedValueOnce({})
      ;(api.resetPassword as any).mockResolvedValueOnce({})
      renderLoginPage()

      await user.click(screen.getByText('¿Olvidaste tu contraseña?'))
      await user.type(screen.getByLabelText('Email Registrado'), 'ana@test.com')
      await user.click(screen.getByRole('button', { name: 'Enviar Código de Recuperación' }))
      await screen.findByLabelText('Código de 6 dígitos')

      await user.type(screen.getByLabelText('Código de 6 dígitos'), '654321')
      const [newPasswordInput, confirmNewPasswordInput] = screen.getAllByPlaceholderText('********')
      await user.type(newPasswordInput, 'Sup3rSecret1')
      await user.type(confirmNewPasswordInput, 'Sup3rSecret1')
      await user.click(screen.getByRole('button', { name: 'Restablecer Contraseña' }))

      await waitFor(() =>
        expect(api.resetPassword).toHaveBeenCalledWith({
          email: 'ana@test.com',
          codigo: '654321',
          newPassword: 'Sup3rSecret1',
        })
      )
      expect(showAlertMock).toHaveBeenCalledWith(
        '¡Tu contraseña fue actualizada con éxito! Ya podés iniciar sesión.',
        'success'
      )
      await waitFor(() => expect(screen.getByLabelText('Email')).toBeInTheDocument())
    })
  })

  describe('Verify email', () => {
    it('shows a warning and does not call the API when the code is not 6 digits', async () => {
      const user = userEvent.setup()
      renderLoginPage()

      // Trigger the verify tab through a failed login that requires verification.
      ;(api.login as any).mockRejectedValueOnce(new Error('Debés verificar tu correo antes de ingresar.'))
      await user.type(screen.getByLabelText('Email'), 'ana@test.com')
      await user.type(screen.getByLabelText('Contraseña'), 'somepass')
      await user.click(getLoginSubmitButton())
      await screen.findByLabelText('Código de Verificación')

      await user.type(screen.getByLabelText('Código de Verificación'), '123')
      await user.click(screen.getByRole('button', { name: 'Verificar y Activar Cuenta' }))

      expect(showAlertMock).toHaveBeenCalledWith(
        'Ingresá el código de 6 dígitos recibido por correo.',
        'warning'
      )
      expect(api.verifyEmail).not.toHaveBeenCalled()
    })

    it('verifies successfully and returns to the login tab with prefilled credentials', async () => {
      const user = userEvent.setup()
      ;(api.login as any).mockRejectedValueOnce(new Error('Debés verificar tu correo antes de ingresar.'))
      ;(api.verifyEmail as any).mockResolvedValueOnce({})
      renderLoginPage()

      await user.type(screen.getByLabelText('Email'), 'ana@test.com')
      await user.type(screen.getByLabelText('Contraseña'), 'somepass')
      await user.click(getLoginSubmitButton())
      await screen.findByLabelText('Código de Verificación')

      await user.type(screen.getByLabelText('Código de Verificación'), '654321')
      await user.click(screen.getByRole('button', { name: 'Verificar y Activar Cuenta' }))

      await waitFor(() =>
        expect(api.verifyEmail).toHaveBeenCalledWith({ email: 'ana@test.com', codigo: '654321' })
      )
      expect(showAlertMock).toHaveBeenCalledWith(
        '¡Email verificado con éxito! Ya podés iniciar sesión.',
        'success'
      )
      await waitFor(() => expect(screen.getByLabelText('Email')).toBeInTheDocument())
    })

    it('resends the verification code', async () => {
      const user = userEvent.setup()
      ;(api.login as any).mockRejectedValueOnce(new Error('Debés verificar tu correo antes de ingresar.'))
      ;(api.resendCode as any).mockResolvedValueOnce({})
      renderLoginPage()

      await user.type(screen.getByLabelText('Email'), 'ana@test.com')
      await user.type(screen.getByLabelText('Contraseña'), 'somepass')
      await user.click(getLoginSubmitButton())
      await screen.findByLabelText('Código de Verificación')

      await user.click(screen.getByRole('button', { name: 'Reenviar código' }))

      await waitFor(() => expect(api.resendCode).toHaveBeenCalledWith({ email: 'ana@test.com' }))
      expect(showAlertMock).toHaveBeenCalledWith('Se envió un nuevo código de verificación a tu correo.', 'success')
    })
  })
})
