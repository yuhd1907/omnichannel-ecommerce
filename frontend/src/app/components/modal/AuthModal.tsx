"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { FiX } from "react-icons/fi";
import { toast } from "sonner";
import { Button } from "@/app/components/button/Button";
import { errorMessage } from "@/helper/api";
import { authModalStore, useAuth } from "@/hooks/useAuth";
import { loginSchema, registerSchema, type LoginInput, type RegisterInput } from "@/schemas/auth.schema";

export function AuthModal() {
  const mode = authModalStore.use();
  const close = () => authModalStore.set(null);

  useEffect(() => {
    if (!mode) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && close();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [mode]);

  if (!mode) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-neutral-900/30 backdrop-blur-sm" onClick={close} />
      <div className="card relative w-full max-w-md p-7" role="dialog" aria-modal="true">
        <button onClick={close} className="absolute top-4 right-4 text-neutral-900 hover:text-neutral-500" aria-label="Đóng">
          <FiX size={20} />
        </button>

        <div className="mb-6 flex rounded-xl bg-neutral-100 p-1">
          {(["login", "register"] as const).map((m) => (
            <button
              key={m}
              onClick={() => authModalStore.set(m)}
              className={`flex-1 rounded-lg py-2 text-sm font-semibold transition ${
                mode === m ? "bg-neutral-900 text-white" : "text-neutral-900 hover:text-neutral-500"
              }`}
            >
              {m === "login" ? "Đăng nhập" : "Đăng ký"}
            </button>
          ))}
        </div>

        {mode === "login" ? <LoginForm onDone={close} /> : <RegisterForm onDone={close} />}
      </div>
    </div>
  );
}

function LoginForm({ onDone }: { onDone: () => void }) {
  const { login } = useAuth();
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginInput>({ resolver: zodResolver(loginSchema) });

  const onSubmit = async (v: LoginInput) => {
    try {
      const user = await login(v.email, v.password);
      toast.success(`Xin chào ${user.fullName}!`);
      onDone();
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
      <div>
        <label className="label" htmlFor="login-email">Email</label>
        <input id="login-email" type="email" autoComplete="email" className="input" autoFocus {...register("email")} />
        {errors.email && <p className="field-error">{errors.email.message}</p>}
      </div>
      <div>
        <label className="label" htmlFor="login-password">Mật khẩu</label>
        <input id="login-password" type="password" autoComplete="current-password" className="input" {...register("password")} />
        {errors.password && <p className="field-error">{errors.password.message}</p>}
      </div>
      <Button type="submit" className="w-full" size="lg" loading={isSubmitting}>
        Đăng nhập
      </Button>
    </form>
  );
}

function RegisterForm({ onDone }: { onDone: () => void }) {
  const { register: registerUser } = useAuth();
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterInput>({ resolver: zodResolver(registerSchema), defaultValues: { phone: "" } });

  const onSubmit = async (v: RegisterInput) => {
    try {
      const user = await registerUser(v);
      toast.success(`Tạo tài khoản thành công. Xin chào ${user.fullName}!`);
      onDone();
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  const fields: { name: keyof RegisterInput; label: string; type: string; auto: string }[] = [
    { name: "fullName", label: "Họ tên", type: "text", auto: "name" },
    { name: "email", label: "Email", type: "email", auto: "email" },
    { name: "phone", label: "Số điện thoại (tùy chọn)", type: "tel", auto: "tel" },
    { name: "password", label: "Mật khẩu", type: "password", auto: "new-password" },
    { name: "confirmPassword", label: "Nhập lại mật khẩu", type: "password", auto: "new-password" },
  ];

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-3.5" noValidate>
      {fields.map((f) => (
        <div key={f.name}>
          <label className="label" htmlFor={`reg-${f.name}`}>{f.label}</label>
          <input id={`reg-${f.name}`} type={f.type} autoComplete={f.auto} className="input" {...register(f.name)} />
          {errors[f.name] && <p className="field-error">{errors[f.name]?.message}</p>}
        </div>
      ))}
      <Button type="submit" className="w-full" size="lg" loading={isSubmitting}>
        Tạo tài khoản
      </Button>
    </form>
  );
}
