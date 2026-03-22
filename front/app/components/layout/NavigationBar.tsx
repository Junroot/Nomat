interface NavigationContent extends React.PropsWithChildren {}

const NavigationBar: React.FC<NavigationContent> = ({children}) => {
     return <div
     className="h-full w-[72px] py-[16px] hidden md:flex flex-col items-center gap-[8px]"
    >
        {children}
    </div>
}

export default NavigationBar